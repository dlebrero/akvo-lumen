(ns load-test
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http-client]
    [clojure.java.io :as io]
    [compojure.core :refer (defroutes GET)])
  (:import (org.keycloak.authorization.client AuthzClient Configuration)
           (org.keycloak.authorization.client.representation AuthorizationRequest PermissionRequest EntitlementRequest ResourceRepresentation ScopeRepresentation)
           (org.keycloak.jose.jws JWSInput)))

(let [{:strs [auth-server-url realm resource credentials]} (json/parse-string (slurp (io/resource "keycloak.json")))]
  (def ^AuthzClient authz
    (AuthzClient/create (Configuration. auth-server-url
                                        realm
                                        resource
                                        credentials
                                        nil))))

(defn create-role [token role]
  (http-client/post "http://localhost:8080/auth/admin/realms/akvo/roles"
                    {:headers {"Content-Type"  "application/json;charset=UTF-8"
                               "Authorization" (str "Bearer " token)}
                     :body    (json/generate-string {:name role})}))

(def num-tenants 3)
(def num-teams 3)
(def num-folders 3)

(defn create-roles []
  (dotimes [tenant num-tenants]
    (let [token (-> authz .obtainAccessToken .getToken)
          tenant-uri (str "tenant:" tenant)]
      (create-role token tenant-uri)
      (dotimes [teamspace num-teams]
        (println "Done " tenant " " teamspace)
        (let [team-uri (str tenant-uri ":teamspace:" teamspace)]
          (create-role token team-uri)
          (let [token (-> authz .obtainAccessToken .getToken)]
            (dotimes [folder num-folders]
              (let [uri (str team-uri ":folder:" folder)]
                (create-role token uri)))))))))

(defn create-resources []
  (time
    (dotimes [tenant num-tenants]
      (dotimes [teamspace num-teams]
        (println "Done " tenant " " teamspace)
        (let [protection (-> authz .protection)]
          (dotimes [folder num-folders]
            (let [uri (str "/tenant/" tenant "/teamspace/" teamspace "/folder/" folder)]
              (-> protection .resource (.create (doto
                                                  (ResourceRepresentation.)
                                                  (.setName uri)
                                                  (.setUri uri)
                                                  ;(.setType (str "tenant" tenant "teamspace" teamspace))
                                                  (.setType (str "folder"))
                                                  (.addScope (doto (ScopeRepresentation.) (.setName "urn:lumen:scopes:dashboard:view")))
                                                  (.addScope (doto (ScopeRepresentation.) (.setName "urn:lumen:scopes:dashboard:delete")))))))))))))

(defn user-token []
  (let [resp (http-client/post "http://localhost:8080/auth/realms/akvo/protocol/openid-connect/token"
                               {:form-params {"grant_type" "password"
                                              "client_id"  "akvo-lumen"
                                              "username"   "jerome"
                                              "password"   "password"}})]
    (-> resp :body json/decode (get "access_token"))))

(comment
  (create-roles)
  (create-resources)

  ;; UMA
  (let [token (user-token)]
    (time (let [perm-response (-> authz
                                  .protection
                                  .permission
                                  (.forResource (doto
                                                  (PermissionRequest.)
                                                  (.setResourceSetName "/tenant/0/teamspace/0/folder/0")
                                                  (.setScopes #{
                                                                ;"urn:lumen:scopes:dashboard:create"
                                                                "urn:lumen:scopes:dashboard:view"
                                                                }))))]
            (-> (.authorization authz token)
                (.authorize (AuthorizationRequest. (.getTicket perm-response)))
                (.getRpt)
                (JWSInput.)
                (.readContentAsString)
                (cheshire.core/parse-string true)
                :authorization
                :permissions))))

  ;; All entitlements
  (let [token (user-token)]
    (time (-> (.entitlement authz token)
              (.getAll (-> authz .getConfiguration .getClientId)) ;; "akvo-lumen-confidential"
              .getRpt
              (JWSInput.)
              (.readContentAsString)
              (cheshire.core/parse-string true)
              :authorization
              :permissions)))

  ;; Several entitlements
  ;; Performance seems reasonable unless:
  ;;     * there is a resource-type permission
  ;;     * you ask for a resource that does not exists
  ;; In either case, KeyCloak loads all the resources. Probably a bug in both cases.
  (let [token (user-token)
        entitlement-request (reduce (fn [^EntitlementRequest ent uri]
                                      (doto ent (.addPermission
                                                  (doto
                                                    (PermissionRequest.)
                                                    (.setResourceSetName uri)
                                                    (.setScopes #{"urn:lumen:scopes:dashboard:view"})))))
                                    (EntitlementRequest.)
                                    (take 100 (for [t (range 10)
                                                    team (range 3)
                                                    folder (range 300)]
                                                (str "/tenant/" t "/teamspace/" team "/folder/" folder))))]
    (time (-> (.entitlement authz token)
              (.get (-> authz .getConfiguration .getClientId)
                    entitlement-request
                    #_(doto
                        (EntitlementRequest.)
                        (.addPermission (doto
                                          (PermissionRequest.)
                                          (.setResourceSetName "/tenant/0/teamspace/0/folder/0")
                                          (.setScopes #{"urn:lumen:scopes:dashboard:view"})))
                        (.addPermission (doto
                                          (PermissionRequest.)
                                          (.setResourceSetName "/tenant/2/teamspace/0/folder/4")
                                          (.setScopes #{"urn:lumen:scopes:dashboard:view"})))))
              .getRpt
              (JWSInput.)
              (.readContentAsString)
              (cheshire.core/parse-string true)
              :authorization
              :permissions)))

  )
