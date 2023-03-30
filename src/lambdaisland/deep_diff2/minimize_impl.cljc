(ns lambdaisland.deep-diff2.minimize-impl
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

(defn has-diff-item?
  "Checks if there are any diff items in x or sub-tree of x"
  [x]
  (or (diff-item? x)
      (and (map? x) (some #(or (has-diff-item? (key %))
                               (has-diff-item? (val %))) x))
      (and (coll? x) (some has-diff-item? x))))

(defn minimize
  "Postwalk diff, removing values that are unchanged"
  [diff]
  (let [y (postwalk
           (fn [x]
             (cond
               (map-entry? x)
               ;; Either k or v of a map-entry contains/is? diff-item,
               ;; keep the map-entry. Otherwise, remove it.
               (when (or (has-diff-item? (key x))
                         (has-diff-item? (val x)))
                 x)

               (map? x)
               x

               (coll? x)
               (into (empty x) (filter has-diff-item?) x)

               :else
               x))
           diff)]
    (cond
      (coll? y) y
      :else     nil)))
