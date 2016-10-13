(ns org.akvo.lumen.transformation.engine
  (:require [hugsql.core :as hugsql]
            [org.akvo.lumen.util :as util])
  (:import java.sql.SQLException))


(hugsql/def-db-fns "org/akvo/lumen/transformation/engine.sql")

(def available-ops
  {"core/change-datatype" nil
   "core/change-column-title" nil
   "core/sort-column" nil
   "core/remove-sort" nil
   "core/filter-column" nil
   "core/to-titlecase" nil
   "core/to-lowercase" nil
   "core/to-uppercase" nil
   "core/trim" nil
   "core/trim-doublespace" nil
   "core/combine" nil})

(defn- get-column-name
  "Returns the columnName from the operation specification"
  [op-spec]
  (get-in op-spec ["args" "columnName"]))

(defn- get-column-idx
  "Returns the column index or a given columnName.
  Throws an exception if not found"
  [columns column-name]
  (let [idx (first
             (keep-indexed #(when (= column-name (get %2 "columnName")) %1) columns))]
    (if (nil? idx)
      (throw (Exception. (str "Column " column-name " not found")))
      idx)))

(defn- get-sort-idx
  "Returns the next sort index for a given vector of columns"
  [columns]
  (inc (count (filter #(get % "sort") columns))))

(defmulti column-metadata-operation
  "Dispatch a columns metadata change"
  (fn [columns op-spec]
    (keyword (get op-spec "op"))))

(defmethod column-metadata-operation :default
  [columns op-spec]
  (throw (ex-info (format "Column metadata changes not supported on operation [%s]"
                          (op-spec "op"))
                  {:columns columns
                   :op-spec op-spec})))

(defmethod column-metadata-operation :core/sort-column
  [columns op-spec]
  (let [col-name (get-column-name op-spec)
        col-idx (get-column-idx columns col-name)
        sort-idx (get-sort-idx columns)
        sort-direction (get-in op-spec ["args" "sortDirection"])]
    (update columns col-idx assoc "sort" sort-idx "direction" sort-direction)))

(defmethod column-metadata-operation :core/remove-sort
  [columns op-spec]
  (let [col-name (get-column-name op-spec)
        col-idx (get-column-idx columns col-name)]
    (update columns col-idx assoc "sort" nil "direction" nil)))

(defmethod column-metadata-operation :core/change-column-title
  [columns op-spec]
  (let [col-name (get-column-name op-spec)
        col-idx (get-column-idx columns col-name)
        col-title (get-in op-spec ["args" "columnTitle"])]
    (update columns col-idx assoc "title" col-title)))

(defmethod column-metadata-operation :core/change-datatype
  [columns op-spec]
  (let [col-name (get-column-name op-spec)
        col-idx (get-column-idx columns col-name)
        new-type (get-in op-spec ["args" "newType"])]
    (update columns col-idx assoc "type" new-type)))


(defmulti apply-operation
  "Applies a particular operation based on `op` key from spec
   * tenant-conn: Open connection to the database
   * table-name: table on which to operate (ds_<uuid>)
   * columns: in-memory representation of a columns spec
   * op-spec: JSON payload with the operation settings
   spec is a JSON payload with the following keys:
   - \"op\" : operation to perform
   - \"args\" : map with arguments to the operation
   - \"onError\" : Error strategy"
  (fn [tenant-conn table-name columns op-spec]
    (keyword (get op-spec "op"))))

(defmethod apply-operation :default
  [tenant-conn table-name columns op-spec]
  {:success? false
   :message (str "Unknown operation " (get op-spec "op"))})

(defmethod apply-operation :core/to-titlecase
  [tenant-conn table-name columns op-spec]
  (db-to-titlecase tenant-conn {:table-name table-name
                                 :column-name (get-column-name op-spec)})
  {:success? true
   :columns columns})

(defmethod apply-operation :core/to-lowercase
  [tenant-conn table-name columns op-spec]
  (db-to-lowercase tenant-conn {:table-name table-name
                                 :column-name (get-column-name op-spec)})
  {:success? true
   :columns columns})

(defmethod apply-operation :core/to-uppercase
  [tenant-conn table-name columns op-spec]
  (db-to-upercase tenant-conn {:table-name table-name
                               :column-name (get-column-name op-spec)})
  {:success? true
   :columns columns})


(defmethod apply-operation :core/trim
  [tenant-conn table-name columns op-spec]
  (db-trim tenant-conn {:table-name table-name
                        :column-name (get-column-name op-spec)})
  {:success? true
   :columns columns})

(defmethod apply-operation :core/trim-doublespace
  [tenant-conn table-name columns op-spec]
  (db-trim-double tenant-conn {:table-name table-name
                               :column-name (get-column-name op-spec)})
  {:success? true
   :columns columns})

(defmethod apply-operation :core/sort-column
  [tenant-conn table-name columns op-spec]
  (let [col-name (get-column-name op-spec)
        idx-name (str table-name "_" col-name)
        new-cols (column-metadata-operation columns op-spec)]
    (db-create-index tenant-conn {:index-name idx-name
                                  :column-name col-name
                                  :table-name table-name})
    {:success? true
     :columns new-cols}))

(defmethod apply-operation :core/remove-sort
  [tenant-conn table-name columns op-spec]
  (let [col-name (get-column-name op-spec)
        idx-name (str table-name "_" col-name)
        new-cols (column-metadata-operation columns op-spec)]
    (db-drop-index tenant-conn {:index-name idx-name})
    {:success? true
     :columns new-cols}))

(defmethod apply-operation :core/change-column-title
  [tenant-conn table-name columns op-spec]
  (let [new-cols (column-metadata-operation columns op-spec)]
    {:success? true
     :columns new-cols}))


(defmethod apply-operation :core/change-datatype
  [tenant-conn table-name columns op-spec]
  (let [new-cols (column-metadata-operation columns op-spec)]
    (try
      (let [result (db-change-data-type tenant-conn {:table-name table-name
                                                     :args (get op-spec "args")
                                                     :on-error (get op-spec "onError")})
            exec-log (vec (:lumen_change_data_type result))]
        {:success? true
         :execution-log exec-log
         :columns new-cols})
      (catch SQLException e
        {:success? false
         :message (.getMessage e)}))))

(defmethod apply-operation :core/filter-column
  [tenant-conn table-name columns op-spec]
  (try
    (let [expr (first (get-in op-spec ["args" "expression"]))
          expr-fn (first expr)
          expr-val (second expr)
          filter-fn (if (= "is" expr-fn) ;; TODO: logic only valid for text columns
                      "="
                      "ilike")
          filter-val (if (= "contains" expr-fn)
                       (str "\"%" expr-val "%\"")
                       (str "\"" expr-val "\""))
          result (db-filter-column tenant-conn {:table-name table-name
                                                :column-name (get-column-name op-spec)
                                                :filter-fn filter-fn
                                                :filter-val filter-val})]
      {:success? true
       :execution-log [(str "Deleted " result " rows")]
       :columns columns})
    (catch Exception e
      {:success? false
       :message (.getMessage e)})))

(defn next-combined-column-name [columns column-name]
  (let [existing-column-names (set (map (fn [column]
                                          (get column "columnName")) columns))]
    (if (contains? existing-column-names column-name)
      (recur columns (str column-name "_0"))
      column-name)))

(defmethod apply-operation :core/combine
  [tenant-conn table-name columns op-spec]
  (try
    (let [new-column-name
          (next-combined-column-name columns (apply str (get-in op-spec ["args" "columnNames"])))
          first-column-name (get-in op-spec ["args" "columnNames" 0])
          second-column-name (get-in op-spec ["args" "columnNames" 1])]
      (add-column tenant-conn {:table-name table-name
                               :new-column-name new-column-name})
      (combine-columns tenant-conn
                       {:table-name table-name
                        :new-column-name new-column-name
                        :first-column first-column-name
                        :second-column second-column-name
                        :separator (get-in op-spec ["args" "separator"])})
      {:success? true
       :execution-log [(format "Combined columns %s, %s into %s"
                               first-column-name second-column-name new-column-name)]
       :columns (conj columns {"title" (get-in op-spec ["args" "newColumnTitle"])
                               "type" "text"
                               "sort" nil
                               "hidden" false
                               "direction" nil
                               "columnName" new-column-name})})
    (catch Exception e
      {:success? false
       :message (:getMessage e)})))

(hugsql/def-db-fns "org/akvo/lumen/transformation.sql")

(defn- deliver-promise-success [promise dataset-id dataset-version-id job-execution-id]
  (deliver promise {:status "OK"
                    :jobExecutionId job-execution-id
                    :datasetVersionId dataset-version-id
                    :datasetId dataset-id}))

(defn- deliver-promise-failure [promise dataset-id job-id message]
  (deliver promise {:status "FAILED"
                    :datasetId dataset-id
                    :jobExecutionId job-id
                    :message message}))

(defn- execute-transformation-failed [completion-promise tenant-conn dataset-id job-id message]
  (update-job-failed-execution tenant-conn {:id job-id :error-log [message]})
  (deliver-promise-failure completion-promise dataset-id job-id message))

(defn execute-transformation
  [completion-promise tenant-conn job-id dataset-id transformation]
  (try
    (let [dataset-version (latest-dataset-version-by-dataset-id tenant-conn {:dataset-id dataset-id})
          columns (vec (:columns dataset-version))
          source-table (:table-name dataset-version)]
      (let [{:keys [success? message columns execution-log]} (apply-operation tenant-conn
                                                                              source-table
                                                                              columns
                                                                              transformation)]
        (println "@execute-transformation")
        (println success?)
        (if success?
          (let [new-dataset-version-id (str (util/squuid))]
            (clear-dataset-version-data-table tenant-conn {:id (:id dataset-version)})
            (new-dataset-version tenant-conn
                                 {:id new-dataset-version-id
                                  :dataset-id dataset-id
                                  :job-execution-id job-id
                                  :table-name source-table
                                  :imported-table-name (:imported-table-name dataset-version)
                                  :version (inc (:version dataset-version))
                                  :transformations (conj (vec (:transformations dataset-version))
                                                         transformation)
                                  :columns columns})
            (update-job-success-execution tenant-conn {:id job-id :exec-log (vec execution-log)})
            (deliver-promise-success completion-promise dataset-id new-dataset-version-id job-id))
          (execute-transformation-failed completion-promise
                                         tenant-conn dataset-id
                                         job-id
                                         message))))
    (catch Exception e
      (execute-transformation-failed completion-promise
                                     tenant-conn
                                     dataset-id
                                     job-id
                                     (.getMessage e)))))

(defn- nothing-to-undo [completion-promise tenant-conn dataset-id dataset-version-id job-id]
  (update-job-success-execution tenant-conn {:id job-id :exec-log []})
  (deliver-promise-success completion-promise dataset-id dataset-version-id job-id))

(defn- apply-undo [completion-promise tenant-conn job-id dataset-id current-dataset-version]
  (let [imported-table-name (:imported-table-name current-dataset-version)
        initial-columns (vec (:columns (dataset-version-by-dataset-id tenant-conn
                                                                      {:dataset-id dataset-id
                                                                       :version 1})))
        table-name (util/gen-table-name "ds")]
    (copy-table tenant-conn
                {:source-table imported-table-name
                 :dest-table table-name}
                {}
                {:transaction? false})
    (loop [transformations (butlast (:transformations current-dataset-version))
           columns initial-columns
           full-execution-log []]
      (if (empty? transformations)
        (let [new-dataset-version-id (str (util/squuid))]
          (clear-dataset-version-data-table tenant-conn {:id (:id current-dataset-version)})
          (new-dataset-version tenant-conn
                               {:id new-dataset-version-id
                                :dataset-id dataset-id
                                :job-execution-id job-id
                                :table-name table-name
                                :imported-table-name (:imported-table-name current-dataset-version)
                                :version (inc (:version current-dataset-version))
                                :transformations (vec
                                                  (butlast
                                                   (:transformations current-dataset-version)))
                                :columns columns})
          (update-job-success-execution tenant-conn {:id job-id :exec-log full-execution-log})
          (deliver-promise-success completion-promise dataset-id new-dataset-version-id job-id))
        (let [{:keys [success? message columns execution-log]}
              (apply-operation tenant-conn table-name columns (first transformations))]
          (if success?
            (recur (rest transformations) columns (into full-execution-log execution-log))
            (execute-transformation-failed completion-promise
                                           tenant-conn
                                           dataset-id
                                           job-id
                                           message)))))))

(defn execute-undo [completion-promise tenant-conn job-id dataset-id]
  (try
    (let [current-dataset-version (latest-dataset-version-by-dataset-id tenant-conn
                                                                        {:dataset-id dataset-id})
          current-version (:version current-dataset-version)]
      (if (= current-version 1)
        (nothing-to-undo completion-promise
                         tenant-conn
                         dataset-id
                         (:id current-dataset-version)
                         job-id)
        (apply-undo completion-promise
                    tenant-conn
                    job-id
                    dataset-id
                    current-dataset-version)))

    (catch Exception e
      (execute-transformation-failed completion-promise
                                     tenant-conn
                                     dataset-id
                                     job-id
                                     (.getMessage e)))))
