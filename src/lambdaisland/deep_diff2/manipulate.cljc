(ns lambdaisland.deep-diff2.manipulate
  "Provide API for manipulate the diff structure data "
  (:require [clojure.walk :refer [postwalk]]
            #?(:clj [lambdaisland.deep-diff2.diff-impl]
               :cljs [lambdaisland.deep-diff2.diff-impl :refer [Mismatch Deletion Insertion]]))
  #?(:clj (:import [lambdaisland.deep_diff2.diff_impl Mismatch Deletion Insertion])))

(defn diff-item?
  "Checks if x is a Mismatch, Deletion, or Insertion"
  [x]
  (or (instance? Mismatch x)
      (instance? Deletion x)
      (instance? Insertion x)))

(defn extend-flatten
  "Flatten, which can apply on hashmap"
  [x]
  (filter (complement coll?)
          (rest (tree-seq coll? seq x))))

(defn has-diff-item?
  "Checks if there are any diff items in x or sub-tree of x"
  [x]
  (some
   #(or (= :- %) (= :+ %))
   (extend-flatten x)))

(defn remove-unchanged
  "Postwalk diff, removing values that are unchanged"
  [diff]
  (postwalk
   (fn [x]
     (cond
       (map-entry? x) (cond
                        (diff-item? (key x)) (do
                                               ;(println "cond 1, keep" x)
                                               x)
                        (has-diff-item? (val x)) (do
                                                    ;(println "cond 2, keep" x)
                                                   x))
       :else          x))
   diff))

