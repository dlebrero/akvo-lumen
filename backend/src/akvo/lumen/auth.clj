(ns akvo.lumen.auth
  (:require
    [clojure.string :as s]
    [ring.util.response :as response]
    [clojure.java.io :as io]
    [compojure.core :refer (defroutes GET)]
    [immutant.web.internal.undertow :as undertow])
  (:import io.undertow.security.api.AuthenticationMode
           [io.undertow.security.handlers AuthenticationCallHandler
                                          AuthenticationConstraintHandler AuthenticationMechanismsHandler
                                          SecurityInitialHandler]
           [io.undertow.security.idm IdentityManager Account Credential]
           [io.undertow.security.impl CachedAuthenticatedSessionMechanism]
           io.undertow.server.HttpServerExchange
           [io.undertow.server.session InMemorySessionManager]
           [org.keycloak.adapters KeycloakDeploymentBuilder AdapterDeploymentContext
                                  NodesRegistrationManagement RefreshableKeycloakSecurityContext]
           [org.keycloak.adapters.undertow UndertowUserSessionManagement
                                           UndertowAuthenticationMechanism UndertowPreAuthActionsHandler
                                           UndertowAuthenticatedActionsHandler OIDCUndertowHttpFacade]))

(def idm (reify IdentityManager
           (^Account verify [_ ^Account account]
             account)
           (^Account verify [_ ^String id ^Credential credential]
             (throw (IllegalStateException. "Should never be called in Keycloak flow")))
           (^Account verify [_ ^Credential credential]
             (throw (IllegalStateException. "Should never be called in Keycloak flow")))))

(defn auth-constraint
  [handler]
  (proxy [AuthenticationConstraintHandler] [handler]
    (isAuthenticationRequired [^HttpServerExchange exchange]
      (let [path (.getRequestPath exchange)]
        ;; TODO: look at public-path?
        (not (.startsWith path "/env"))))))

(defn wrap-backwards-compatible-jwt-like [handler]
  (fn [request]
    (if-let [token (some-> request
                           ^io.undertow.server.HttpServerExchange (get :server-exchange)
                           ^RefreshableKeycloakSecurityContext (.getAttachment OIDCUndertowHttpFacade/KEYCLOAK_SECURITY_CONTEXT_KEY)
                           (.getToken))]
      (handler (assoc request :jwt-claims {"name" (.getName token)
                                           "subject" (.getSubject token)
                                           "realm_access" {"roles" (some-> token .getRealmAccess .getRoles)}}))
      (handler request))))

;; Workaround: the authz filter throws an exception when trying to write a error response at io.undertow.server.HttpServerExchange.getOutputStream
;; Proper fix: for org.keycloak.adapters.undertow.UndertowHttpFacade.ResponseFacade.sendError(int, java.lang.String)
;;             to call this.getOutputStream() instead of exchange.getOutputStream()
(defn start-blocking [handler]
  (reify io.undertow.server.HttpHandler
    (handleRequest [this exchange]
      (when (not (.isBlocking exchange))
        (.startBlocking exchange))
      (.handleRequest handler exchange))))

(defn wrap-jwt [handler _]
  (let [session-manager (InMemorySessionManager. "SESSION_MANAGER")
        deployment (KeycloakDeploymentBuilder/build (io/input-stream (io/resource "keycloak.json")))
        deployment-context (AdapterDeploymentContext. deployment)
        session-management (UndertowUserSessionManagement.)
        nodes-management (NodesRegistrationManagement.)
        auth-mechanisms [(CachedAuthenticatedSessionMechanism.)
                         (UndertowAuthenticationMechanism. deployment-context
                                                           session-management
                                                           nodes-management
                                                           -1
                                                           nil)]
        ]
    (.registerSessionListener session-manager session-management)
    (as-> (wrap-backwards-compatible-jwt-like handler) $
          (undertow/create-http-handler $)
          (UndertowAuthenticatedActionsHandler. deployment-context $) ;; this guy just works if the session is already in the HttpRequest.
                                                                      ;; Does nothing otherwise: AuthenticatedActionsHandler.java:146
          (AuthenticationCallHandler. $)                    ;; needed?
          (auth-constraint $)
          (AuthenticationMechanismsHandler. $ auth-mechanisms)
          (UndertowPreAuthActionsHandler. deployment-context session-management session-manager $)
          (start-blocking $)
          (SecurityInitialHandler. AuthenticationMode/PRO_ACTIVE idm $))))

(defn claimed-roles [jwt-claims]
  (set (get-in jwt-claims ["realm_access" "roles"])))

(defn tenant-user?
  [{:keys [tenant jwt-claims]}]
  (contains? (claimed-roles jwt-claims)
             (format "akvo:lumen:%s" tenant)))

(defn tenant-admin?
  [{:keys [tenant jwt-claims]}]
  (contains? (claimed-roles jwt-claims)
             (format "akvo:lumen:%s:admin" tenant)))

(defn public-path? [{:keys [path-info request-method]}]
  (and (= :get request-method)
       (or (= "/api" path-info)
           (= "/env" path-info)
           (s/starts-with? path-info "/share/")
           (s/starts-with? path-info "/verify/"))))

(defn admin-path? [{:keys [path-info]}]
  (s/starts-with? path-info "/api/admin/"))

(defn api-path? [{:keys [path-info]}]
  (s/starts-with? path-info "/api/"))

(def not-authenticated
  (-> (response/response "Not authenticated")
      (response/status 401)))

(def not-authorized
  (-> (response/response "Not authorized")
      (response/status 403)))

(defn wrap-auth
  "Wrap authentication for API. Allow GET to root / and share urls at /s/<id>.
  If request don't contain claims return 401. If current dns label (tenant) is
  not in claimed roles return 403.
  Otherwiese grant access. This implies that access is on tenant level."
  [handler]
  (fn [{:keys [jwt-claims] :as request}]
    (cond
      (public-path? request) (handler request)
      (nil? jwt-claims) not-authenticated
      (admin-path? request) (if (tenant-admin? request)
                              (handler request)
                              not-authorized)
      (api-path? request) (if (tenant-user? request)
                            (handler request)
                            not-authorized)
      :else not-authorized)))
