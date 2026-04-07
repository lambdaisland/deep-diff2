(ns lambdaisland.deep-diff2.minimise-impl
  "Provide API for manipulate the diff structure data "
  (:require
   [clojure.walk :refer [postwalk]]
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

(defn minimise
  "Postwalk diff, removing values that are unchanged"
  [o]
  (cond
    (diff-item? o)
    o

    (map-entry? o)
    (cond
      (has-diff-item? (key o))
      o
      (has-diff-item? (val o))
      #?(:clj
         (clojure.lang.MapEntry/create (key o) (minimise (val o)))
         :cljs
         (MapEntry. (key o) (minimise (val o)) nil)))

    (record? o)
    (into o (map minimise) o)

    (map? o)
    (into {} (keep minimise) o)

    (coll? o)
    (if (has-diff-item? o)
      (into (empty o) (keep minimise) o)
      (empty o))

    :else
    nil))
