(ns lambdaisland.deep-diff.printer-test
  (:require [clojure.test :refer :all]
            [lambdaisland.deep-diff.diff :as diff]
            [lambdaisland.deep-diff.printer :as printer]))

(deftest print-doc-test
  (let [printer (printer/puget-printer {})]
    (is (= "[31m-#inst \"2019-04-09T14:57:46.128-00:00\"[0m [32m+#inst \"2019-04-10T14:57:46.128-00:00\"[0m\n"
           (with-out-str (-> (diff/diff #inst "2019-04-09T14:57:46.128-00:00"
                                        #inst "2019-04-10T14:57:46.128-00:00")
                             (printer/format-doc printer)
                             (printer/print-doc printer)))))))
