(ns lambdaisland.deep-diff2.runner
  "Test runner for babashka, until kaocha works with bb :)"
  (:require [clojure.test :as t]))

(defn run-tests [_]
  (let [test-nss '[lambdaisland.deep-diff2.diff-test
                   #_lambdaisland.deep-diff2.printer-test
                   lambdaisland.deep-diff2.puget-test]]
    (doseq [test-ns test-nss]
      (require test-ns))
    (let [{:keys [fail error]}
          (apply t/run-tests test-nss)]
      (when (and fail error (pos? (+ fail error)))
        (throw (ex-info "Tests failed" {:babashka/exit 1}))))))
