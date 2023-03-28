(ns lambdaisland.deep-diff2.strip-test
  (:require [clojure.test :refer [deftest testing is are]]
            [lambdaisland.deep-diff2.diff-impl :as diff]
            [lambdaisland.deep-diff2.strip :as strip]
            [lambdaisland.deep-diff2 :as ddiff]))

(deftest strip-test
  (testing "removing the same items"
    (let [x {:a 1 :b 2 :d {:e 1} :g [:e [:k 14 :g 15]]}
          y {:a 1 :c 3 :d {:e 15} :g [:e [:k 14 :g 15]]}]
      (is (= (ddiff/diff x y)
             {:a 1
              (diff/->Deletion :b) 2
              :d {:e (diff/->Mismatch 1 15)}
              :g [:e [:k 14 :g 15]]
              (diff/->Insertion :c) 3}))))
  (testing "removing the same items"
    (let [x {:a 1 :b 2 :d {:e 1} :g [:e [:k 14 :g 15]]}
          y {:a 1 :c 3 :d {:e 15} :g [:e [:k 14 :g 15]]}]
      (is (= (strip/remove-unchanged (ddiff/diff x y))
             {(diff/->Deletion :b) 2
              :d {:e (diff/->Mismatch 1 15)}
              (diff/->Insertion :c) 3})))))
