(ns org.akvo.lumen.endpoint.dataset
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.string :as str]
            [compojure.core :refer :all]
            [hugsql.core :as hugsql]
            [org.akvo.lumen.component.tenant-manager :refer [connection]]
            [org.akvo.lumen.endpoint.job-execution :as job-execution]
            [org.akvo.lumen.import :as import]
            [ring.util.response :refer (not-found response)]))

(hugsql/def-db-fns "org/akvo/lumen/endpoint/dataset.sql")
(hugsql/def-db-fns "org/akvo/lumen/job-execution.sql")

(defn select-data-sql [table-name columns limit offset]
  (let [column-names (map #(get % "columnName") columns)
        f (fn [m] (get m "sort"))
        sort-columns (conj
                      (vec
                       (for [c (sort-by f (filter f columns))]
                         (str (get c "columnName") " " (get c "direction"))))
                      "rnum")
        order-by-expr (str "ORDER BY " (str/join "," sort-columns))
        limit-expr (if limit
                     (str "LIMIT " (Integer/parseInt limit))
                     "")
        offset-expr (if offset
                      (str "OFFSET " (Integer/parseInt offset))
                      "")
        sql (format "SELECT %s FROM %s %s %s %s"
                    (str/join "," column-names)
                    table-name
                    order-by-expr
                    limit-expr
                    offset-expr)]
    sql))

(defn find-dataset [conn id {:keys [limit offset]}]
  (when-let [dataset (dataset-by-id conn {:id id})]
    (let [columns (remove #(get % "hidden") (:columns dataset))
          data (rest (jdbc/query conn
                                 [(select-data-sql (:table-name dataset) columns limit offset)]
                                 {:as-arrays? true}))]
      {:id id
       :name (:title dataset)
       :modified (:modified dataset)
       :created (:created dataset)
       :status "OK"
       :transformations (:transformations dataset)
       :columns columns
       :rows data})))


(defn endpoint [{:keys [tenant-manager config]}]
  (context "/api/datasets" {:keys [params tenant] :as request}
    (let-routes [tenant-conn (connection tenant-manager tenant)]

      (GET "/" _
        (response (all-datasets tenant-conn)))

      (POST "/" {:keys [tenant body jwt-claims] :as request}
        (import/handle-import-request tenant-conn config jwt-claims body))

      (context "/:id" [id]
        (GET "/" _
          (if-let [dataset (find-dataset tenant-conn id params)]
            (response dataset)
            (not-found {:id id})))

        (DELETE "/" _
          (let [c (delete-dataset-by-id tenant-conn {:id id})]
            (when (zero? c)
              (delete-failed-job-execution-by-id tenant-conn {:id id})))
          (response {:id id}))))))
