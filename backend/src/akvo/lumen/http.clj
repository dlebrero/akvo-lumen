(ns akvo.lumen.http)

(defn- response [response-code]
  {:pre [(pos? response-code)]}
  (fn [body]
    {:pre [(map? body)]}
    {:status response-code
     :body body}))

(def ok (response 200))
(def created (response 201))
(def bad-request (response 400))
(def not-found (response 404))
(def internal-server-error (response 500))
