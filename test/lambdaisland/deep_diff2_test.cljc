(ns lambdaisland.deep-diff2-test
  "Smoke tests of the top level API."
  (:require [lambdaisland.deep-diff2 :as ddiff]
            [lambdaisland.deep-diff2.diff-impl :as diff-impl]
            [clojure.test :refer [is are deftest testing]]
            [clojure.string :as str]))

(deftest diff-test
  (is (= [{:foo (diff-impl/->Mismatch 1 2)}]
         (ddiff/diff [{:foo 1}] [{:foo 2}]))))

(deftest printer-test
  (is (instance? lambdaisland.deep_diff2.puget.printer.PrettyPrinter
                 (ddiff/printer))))

(deftest pretty-print-test
  (is (= "\u001B[1;31m[\u001B[0m\u001B[1;31m{\u001B[0m\u001B[1;33m:foo\u001B[0m \u001B[31m-1\u001B[0m \u001B[32m+2\u001B[0m\u001B[1;31m}\u001B[0m\u001B[1;31m]\u001B[0m\n"
         (with-out-str
           (ddiff/pretty-print (ddiff/diff [{:foo 1}] [{:foo 2}]))))))
