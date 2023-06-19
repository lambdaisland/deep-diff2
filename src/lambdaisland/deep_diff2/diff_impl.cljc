(ns lambdaisland.deep-diff2.diff-impl
  (:require [clojure.data :as data]
            [clojure.set :as set]
            [lambdaisland.clj-diff.core :as seq-diff]))

(declare diff diff-similar)

(defrecord Mismatch [- +])
(defrecord Deletion [-])
(defrecord Insertion [+])

(defprotocol Diff
  (-diff-similar [x y]))

;; For property based testing
(defprotocol Undiff
  (left-undiff [x])
  (right-undiff [x]))

(defn shift-insertions [ins]
  (reduce (fn [res idx]
            (let [offset (apply + (map count (vals res)))]
              (assoc res (+ idx offset) (get ins idx))))
          {}
          (sort (keys ins))))

(defn replacements
  "Given a set of deletion indexes and a map of insertion index to value sequence,
  match up deletions and insertions into replacements, returning a map of
  replacements, a set of deletions, and a map of insertions."
  [[del ins]]
  ;; Loop over deletions, if they match up with an insertion, turn them into a
  ;; replacement. This could be a reduce over (sort del) tbh but it's already a
  ;; lot more readable than the first version.
  (loop [rep {}
         del del
         del-rest (sort del)
         ins ins]
    (if-let [d (first del-rest)]
      (if-let [i (seq (get ins d))] ;; matching insertion
        (recur (assoc rep d (first i))
               (disj del d)
               (next del-rest)
               (update ins d next))

        (if-let [i (seq (get ins (dec d)))]
          (recur (assoc rep d (first i))
                 (disj del d)
                 (next del-rest)
                 (-> ins
                     (dissoc (dec d))
                     (assoc d (seq (concat (next i)
                                           (get ins d))))))
          (recur rep
                 del
                 (next del-rest)
                 ins)))
      [rep del (into {}
                     (remove (comp nil? val))
                     (shift-insertions ins))])))

(defn del+ins
  "Wrapper around clj-diff that returns deletions and insertions as a set and map
  respectively."
  [exp act]
  (let [{del :- ins :+} (seq-diff/diff exp act)]
    [(into #{} del)
     (into {} (map (fn [[k & vs]] [k (vec vs)])) ins)]))

(defn diff-seq-replacements [replacements s]
  (map-indexed
   (fn [idx v]
     (if (contains? replacements idx)
       (diff v (get replacements idx))
       v))
   s))

(defn diff-seq-deletions [del s]
  (map
   (fn [v idx]
     (if (contains? del idx)
       (->Deletion v)
       v))
   s
   (range)))

(defn diff-seq-insertions [ins s]
  (reduce (fn [res [idx vs]]
            (concat (take (inc idx) res) (map ->Insertion vs) (drop (inc idx) res)))
          s
          ins))

(defn diff-seq [exp act]
  (let [[rep del ins] (replacements (del+ins exp act))]
    (->> exp
         (diff-seq-replacements rep)
         (diff-seq-deletions del)
         (diff-seq-insertions ins)
         (into []))))

(defn diff-set [exp act]
  (into
   (into #{}
         (map (fn [e]
                (if (contains? act e)
                  e
                  (->Deletion e))))
         exp)
   (map ->Insertion)
   (remove #(contains? exp %) act)))

(defn diff-map [exp act]
  (let [exp-ks (set (keys exp))
        act-ks (set (keys act))]
    (reduce
      (fn [m k]
        (case [(contains? exp-ks k) (contains? act-ks k)]
          [true false]
          (assoc m (->Deletion k) (get exp k))
          [false true]
          (assoc m (->Insertion k) (get act k))
          [true true]
          (assoc m k (diff (get exp k) (get act k)))))
      {}
      (set/union exp-ks act-ks))))

(defn primitive? [x]
  (or (number? x) (string? x) (boolean? x) (inst? x) (keyword? x) (symbol? x)))

(defn diff-atom [exp act]
  (if (= exp act)
    exp
    (->Mismatch exp act)))

(defn diff-similar [x y]
  (if (primitive? x)
    (diff-atom x y)
    (-diff-similar x y)))

(defn diffable? [exp]
  (satisfies? Diff exp))

;; ClojureScript has this, Clojure doesn't
#?(:clj
   (defn array? [x]
     (and x (.isArray (class x)))))

(defn diff [exp act]
  (cond
    (nil? exp)
    (diff-atom exp act)

    (and (diffable? exp)
         (= (data/equality-partition exp) (data/equality-partition act)))
    (diff-similar exp act)

    (array? exp)
    (diff-seq exp act)

    (record? exp)
    (diff-map exp act)

    :else
    (diff-atom exp act)))

(extend-protocol Diff
  #?(:clj java.util.Set :cljs cljs.core/PersistentHashSet)
  (-diff-similar [exp act]
    (diff-set exp act))
  #?@(:clj
      [java.util.List
       (-diff-similar [exp act] (diff-seq exp act))

       java.util.Map
       (-diff-similar [exp act] (diff-map exp act))]

      :cljs
      [cljs.core/List
       (-diff-similar [exp act] (diff-seq exp act))

       cljs.core/PersistentVector
       (-diff-similar [exp act] (diff-seq exp act))

       cljs.core/EmptyList
       (-diff-similar [exp act] (diff-seq exp act))

       cljs.core/PersistentHashMap
       (-diff-similar [exp act] (diff-map exp act))

       cljs.core/PersistentArrayMap
       (-diff-similar [exp act] (diff-map exp act))]))

(extend-protocol Undiff
  Mismatch
  (left-undiff [m] (get m :-))
  (right-undiff [m] (get m :+))

  Insertion
  (right-undiff [m] (get m :+))

  Deletion
  (left-undiff [m] (get m :-))

  nil
  (left-undiff [m] m)
  (right-undiff [m] m)

  #?(:clj Object :cljs default)
  (left-undiff [m] m)
  (right-undiff [m] m)

  #?@(:clj
      [java.util.List
       (left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s)))
       (right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s)))

       java.util.Set
       (left-undiff [s] (set (left-undiff (seq s))))
       (right-undiff [s] (set (right-undiff (seq s))))

       java.util.Map
       (left-undiff [m]
                    (into {}
                          (comp (remove #(instance? Insertion (key %)))
                                (map (juxt (comp left-undiff key) (comp left-undiff val))))
                          m))
       (right-undiff [m]
                     (into {}
                           (comp (remove #(instance? Deletion (key %)))
                                 (map (juxt (comp right-undiff key) (comp right-undiff val))))
                           m))]

      :cljs
      [cljs.core/List
       (left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s)))
       (right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s)))

       cljs.core/EmptyList
       (left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s)))
       (right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s)))

       cljs.core/PersistentHashSet
       (left-undiff [s] (set (left-undiff (seq s))))
       (right-undiff [s] (set (right-undiff (seq s))))

       cljs.core/PersistentTreeSet
       (left-undiff [s] (set (left-undiff (seq s))))
       (right-undiff [s] (set (right-undiff (seq s))))

       cljs.core/PersistentVector
       (left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s)))
       (right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s)))

       cljs.core/KeySeq
       (left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s)))
       (right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s)))

       cljs.core/PersistentArrayMap
       (left-undiff [m]
                    (into {}
                          (comp (remove #(instance? Insertion (key %)))
                                (map (juxt (comp left-undiff key) (comp left-undiff val))))
                          m))
       (right-undiff [m]
                     (into {}
                           (comp (remove #(instance? Deletion (key %)))
                                 (map (juxt (comp right-undiff key) (comp right-undiff val))))
                           m))

       cljs.core/PersistentHashMap
       (left-undiff [m]
                    (into {}
                          (comp (remove #(instance? Insertion (key %)))
                                (map (juxt (comp left-undiff key) (comp left-undiff val))))
                          m))
       (right-undiff [m]
                     (into {}
                           (comp (remove #(instance? Deletion (key %)))
                                 (map (juxt (comp right-undiff key) (comp right-undiff val))))
                           m))

       cljs.core/UUID
       (left-undiff [m] m)
       (right-undiff [m] m)]))
