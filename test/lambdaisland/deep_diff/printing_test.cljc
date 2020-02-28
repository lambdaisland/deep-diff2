(ns lambdaisland.deep-diff.printing-test
  (:require [clojure.test :refer [deftest testing is are]]
            [lambdaisland.deep-diff.diffing :as diff]
            [lambdaisland.deep-diff.printing :as printer])
  #?(:clj
     (:import (java.sql Timestamp)
              (java.util Date
                         GregorianCalendar
                         TimeZone))))

(defn- printed
  [diff]
  (let [printer (printer/puget-printer {})]
    (with-out-str (-> diff
                      (printer/format-doc printer)
                      (printer/print-doc printer)))))
#?(:clj
   (defn- calendar
     [date]
     (doto (GregorianCalendar. (TimeZone/getTimeZone "GMT"))
       (.setTime date))))

(deftest print-doc-test
  (testing "date"
    (is (= "\u001B[31m-#inst \"2019-04-09T14:57:46.128-00:00\"\u001B[0m \u001B[32m+#inst \"2019-04-10T14:57:46.128-00:00\"\u001B[0m\n"
           (printed (diff/diff #inst "2019-04-09T14:57:46.128-00:00"
                               #inst "2019-04-10T14:57:46.128-00:00")))))

  #?(:clj
     (testing "timestamp"
       (is (= "\u001B[31m-#inst \"1970-01-01T00:00:00.000000000-00:00\"\u001B[0m \u001B[32m+#inst \"1970-01-01T00:00:01.000000101-00:00\"\u001B[0m\n"
              (printed (diff/diff (Timestamp. 0)
                                  (doto (Timestamp. 1000) (.setNanos 101))))))))

  #?(:clj
     (testing "calendar"
       (is (= "\u001B[31m-#inst \"1970-01-01T00:00:00.000+00:00\"\u001B[0m \u001B[32m+#inst \"1970-01-01T00:00:01.001+00:00\"\u001B[0m\n"
              (printed (diff/diff (calendar (Date. 0)) (calendar (Date. 1001))))))))

  (testing "uuid"
    (is (= "\u001B[31m-#uuid \"e41b325a-ce9d-4fdd-b51d-280d9c91314d\"\u001B[0m \u001B[32m+#uuid \"0400be9a-619f-4c6a-a735-6245e4955995\"\u001B[0m\n"
           (printed (diff/diff #uuid "e41b325a-ce9d-4fdd-b51d-280d9c91314d"
                               #uuid "0400be9a-619f-4c6a-a735-6245e4955995"))))))
