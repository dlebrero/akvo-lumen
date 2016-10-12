(ns org.akvo.lumen.import.csv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as s]
            [hugsql.core :as hugsql]
            [org.akvo.lumen.import.common :as import]
            [org.akvo.lumen.util :refer [squuid]])
  (:import java.util.UUID
           org.postgresql.PGConnection
           org.postgresql.copy.CopyManager
           com.ibm.icu.text.CharsetDetector))

(defn- get-cols
  ([num-cols]
   (get-cols num-cols nil))
  ([num-cols c-type]
   (s/join ", "
           (for [i (range 1 (inc num-cols))]
             (str "c" i
                  (if-not (nil? c-type)
                    (str " " c-type)
                    ""))))))

#_(defn- gen-table-name
  []
  (str "ds_" (s/replace (UUID/randomUUID) #"-" "_")))

#_(defn get-create-table-sql
  "Returns a `CREATE TABLE` statement for
  the given number table name and number of columns"
  [t-name num-cols c-type temp?]
  (format "CREATE %s %s (%s, %s)"
          (if temp? "TEMP TABLE" "TABLE")
          t-name
          (if temp? "rnum serial primary key" "rnum integer primary key")
          (get-cols num-cols c-type)))

(defn get-create-table-sql
  "Returns a `CREATE TABLE` statement for
  the given number table name and number of columns"
  [t-name num-cols c-type temp?]
  (if temp?
    (format "CREATE TEMP TABLE %s (%s, %s)"
            t-name
            (if temp? "rnum serial primary key" "rnum integer primary key")
            (get-cols num-cols c-type))
    (format "CREATE TABLE dataset.%s (%s, %s)"
            t-name
            (if temp? "rnum serial primary key" "rnum integer primary key")
            (get-cols num-cols c-type))))

(defn get-copy-sql
  "Returns a `COPY` statement for the given
  table and number of columns"
  [t-name num-cols headers? encoding]
  (format "COPY %s (%s) FROM STDIN WITH (FORMAT CSV, ENCODING '%s'%s)"
          t-name
          (get-cols num-cols)
          encoding
          (if headers? ", HEADER true" "")))

(defn get-insert-sql
  "Returns an `INSERT` statement for a given
  table and number of columns."
  [src-table dest-table num-cols]
  (let [cols (for [i (range 1 (inc num-cols))]
               (str "c" i))
        src-cols (map #(format "to_json(replace(%s, '\\', '\\\\'))::jsonb" %) cols)]
    (format "INSERT INTO dataset.%s (rnum, %s) SELECT rnum, %s FROM %s"
            dest-table
            (s/join ", " cols)
            (s/join ", " src-cols)
            src-table)))

(defn get-vacuum-sql
  "Returns a `VACUUM` statement for a given table"
  [table-name]
  (format "VACUUM (FULL) dataset.%s" table-name))

(defn get-drop-table-sql
  "Returns a `DROP TABLE` statement for a given table"
  [table-name]
  (format "DROP TABLE IF EXISTS %s CASCADE" table-name))

(defn get-headers
  "Returns the first line CSV a file"
  [path separator encoding]
  (with-open [r (io/reader path :encoding encoding)]
    (first (csv/read-csv r :separator separator))))

(defn get-num-cols
  "Returns the number of columns based on the
  first line of a CSV file"
  [path separator encoding]
  (count (get-headers path separator encoding)))

(defn get-encoding
  "Returns the character encoding reading some
  bytes from the file. It uses ICU's CharsetDetector"
  [path]
  (let [detector (CharsetDetector.)
        ba (byte-array 4096)]
    (with-open [is (io/input-stream path)]
      (.read is ba))
    (-> (.setText detector ba)
        (.detect)
        (.getName))))

(defn get-column-tuples
  [col-titles]
  (vec
   (map-indexed (fn [idx title]
                  {:title title
                   :column-name (str "c" (inc idx))
                   :type "text"})
                col-titles)))

(defmethod import/valid? "CSV"
  [{:strs [url fileName hasColumnHeaders]}]
  (and (string? url)
       (contains? #{true false nil} hasColumnHeaders)
       (or (nil? fileName)
           (string? fileName))))

(defmethod import/authorized? "CSV"
  [claims config spec]
  true)

(defn- get-path
  [spec file-upload-path]
  (or (get spec "path")
      (let [file-on-disk? (contains? spec "fileName")
            url (get spec "url")]
                 (if file-on-disk?
                   (str file-upload-path
                        "/resumed/"
                        (last (s/split url #"\/"))
                        "/file")
                   url))))

(defmethod import/make-dataset-data-table "CSV"
  [tenant-conn {:keys [file-upload-path]} table-name spec]
  (try
    (let [ ;; TODO a bit of "manual" integration work
          path (get-path spec file-upload-path)
          headers? (boolean (get spec "hasColumnHeaders"))
          encoding (get-encoding path)
          n-cols (get-num-cols path \, encoding)
          col-titles (if headers?
                       (get-headers path \, encoding)
                       (vec (for [i (range 1 (inc n-cols))]
                              (str "Column " i))))
          temp-table (str table-name "_temp")
          copy-sql (get-copy-sql temp-table n-cols headers? encoding)]
      (jdbc/execute! tenant-conn [(get-create-table-sql temp-table n-cols "text" true)])
      (jdbc/execute! tenant-conn [(get-create-table-sql table-name n-cols "jsonb" false)])
      (with-open [conn (-> tenant-conn :datasource .getConnection)]
        (let [copy-manager (.getCopyAPI (.unwrap conn PGConnection))]
          (.copyIn copy-manager copy-sql (io/input-stream path))))
      (jdbc/execute! tenant-conn [(get-insert-sql temp-table table-name n-cols)])
      (jdbc/execute! tenant-conn [(get-drop-table-sql temp-table)])
      (jdbc/execute! tenant-conn [(get-vacuum-sql table-name)] {:transaction? false})
      {:success? true
       :columns (get-column-tuples col-titles)})
    (catch Exception e
      {:success? false
       :reason (str "Unexpected error " (.getMessage e))})) )
