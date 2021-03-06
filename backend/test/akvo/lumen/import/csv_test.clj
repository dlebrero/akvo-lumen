(ns akvo.lumen.import.csv-test
  (:require [akvo.lumen.fixtures :refer [test-conn]]
            [akvo.lumen.import :refer [do-import]]
            [akvo.lumen.import.csv :refer :all]
            [akvo.lumen.util :refer [squuid]]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "akvo/lumen/job-execution.sql")
(hugsql/def-db-fns "akvo/lumen/transformation.sql")


(defn import-file
  "Import a file and return the dataset-id"
  [file {:keys [dataset-name has-column-headers?]}]
  (let [data-source-id (str (squuid))
        job-id (str (squuid))
        data-source-spec {"name" (or dataset-name file)
                          "source" {"path" (.getAbsolutePath (io/file (io/resource file)))
                                    "kind" "DATA_FILE"
                                    "fileName" (or dataset-name file)
                                    "hasColumnHeaders" (boolean has-column-headers?)}}]
    (insert-data-source test-conn {:id data-source-id :spec data-source-spec})
    (insert-job-execution test-conn {:id job-id :data-source-id data-source-id})
    (do-import test-conn {:file-upload-path "/tmp/akvo/dash"} job-id)
    (:dataset_id (dataset-id-by-job-execution-id test-conn {:id job-id}))))

(deftest ^:functional test-dos-file
  (testing "Import of DOS-formatted CSV file"
    (let [dataset-id (import-file "dos.csv" {:dataset-name "DOS data"})
          dataset (dataset-version-by-dataset-id test-conn {:dataset-id dataset-id
                                                            :version 1})]
      (is (= 2 (count (:columns dataset)))))))
