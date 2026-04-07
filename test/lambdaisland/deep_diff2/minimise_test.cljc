(ns lambdaisland.deep-diff2.minimise-test
  (:require
   [clojure.test :refer [deftest testing is are]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [lambdaisland.deep-diff2 :as ddiff]
   [lambdaisland.deep-diff2.diff-impl :as diff]
   [lambdaisland.deep-diff2.diff-test :as diff-test]))

(deftest basic-strip-test
  (testing "diff without minimise"
    (let [x {:a 1 :b 2 :d {:e 1} :g [:e [:k 14 :g 15]]}
          y {:a 1 :c 3 :d {:e 15} :g [:e [:k 14 :g 15]]}]
      (is (= (ddiff/diff x y)
             {:a 1
              (diff/->Deletion :b) 2
              :d {:e (diff/->Mismatch 1 15)}
              :g [:e [:k 14 :g 15]]
              (diff/->Insertion :c) 3}))))
  (testing "diff with minimise"
    (let [x {:a 1 :b 2 :d {:e 1} :g [:e [:k 14 :g 15]]}
          y {:a 1 :c 3 :d {:e 15} :g [:e [:k 14 :g 15]]}]
      (is (= (ddiff/minimise (ddiff/diff x y))
             {(diff/->Deletion :b) 2
              :d {:e (diff/->Mismatch 1 15)}
              (diff/->Insertion :c) 3})))))

(deftest minimise-on-diff-test
  (testing "diffing atoms"
    (testing "when different"
      (is (= (ddiff/minimise
              (ddiff/diff :a :b))
             (diff/->Mismatch :a :b))))

    (testing "when equal"
      (is (= (ddiff/minimise
              (ddiff/diff :a :a))
             nil))))

  (testing "diffing collections"
    (testing "when different collection types"
      (is (= (ddiff/minimise
              (ddiff/diff [:a :b] #{:a :b}))
             (diff/->Mismatch [:a :b] #{:a :b}))))

    (testing "when equal with clojure set"
      (is (= (ddiff/minimise
              (ddiff/diff #{:a :b} #{:a :b}))
             #{})))

    (testing "when different with clojure set"
      (is (= (ddiff/minimise
              (ddiff/diff #{:a :b :c} #{:a :b :d}))
             #{(diff/->Insertion :d) (diff/->Deletion :c)})))

    (testing "when equal with clojure vector"
      (is (= (ddiff/minimise
              (ddiff/diff [:a :b] [:a :b]))
             [])))

    (testing "when equal with clojure hashmap"
      (is (= (ddiff/minimise
              (ddiff/diff {:a 1} {:a 1}))
             {})))

    (testing "when equal with clojure nesting vector"
      (is (= (ddiff/minimise
              (ddiff/diff [:a [:b :c :d]] [:a [:b :c :d]]))
             [])))

    (testing "inserting a new map"
      (is
       (= (ddiff/minimise (ddiff/diff {} {:foo {:a 1}}))
          {(diff/->Insertion :foo) {:a 1}})))))

;; "diff itself and minimise yields empty"
(defspec diff-itself 100
  (prop/for-all
   [x diff-test/gen-any-except-NaN]
   (if (coll? x)
     (= (ddiff/minimise (ddiff/diff x x))
        (empty x))
     (nil? (ddiff/minimise (ddiff/diff x x))))))
