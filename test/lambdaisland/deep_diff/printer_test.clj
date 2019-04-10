(ns lambdaisland.deep-diff.printer-test
  (:require [clojure.test :refer :all]
            [lambdaisland.deep-diff.diff :as diff]
            [lambdaisland.deep-diff.printer :as printer]))

(defn- printed
  [diff]
  (let [printer (printer/puget-printer {})]
    (with-out-str (-> diff
                      (printer/format-doc printer)
                      (printer/print-doc printer)))))

(deftest print-doc-test
  (testing "date"
    (is (= "\u001B[31m-#inst \"2019-04-09T14:57:46.128-00:00\"\u001B[0m \u001B[32m+#inst \"2019-04-10T14:57:46.128-00:00\"\u001B[0m\n"
           (printed (diff/diff #inst "2019-04-09T14:57:46.128-00:00"
                               #inst "2019-04-10T14:57:46.128-00:00")))))
  (testing "uuid"
    (is (= "\u001B[31m-#uuid \"e41b325a-ce9d-4fdd-b51d-280d9c91314d\"\u001B[0m \u001B[32m+#uuid \"0400be9a-619f-4c6a-a735-6245e4955995\"\u001B[0m\n"
           (printed (diff/diff #uuid "e41b325a-ce9d-4fdd-b51d-280d9c91314d"
                               #uuid "0400be9a-619f-4c6a-a735-6245e4955995"))))))
