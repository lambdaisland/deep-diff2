(ns lambdaisland.deep-diff.diff
  (:require [clojure.data :as data]
            [clj-diff.core :as seq-diff]))

(declare diff diff-similar left-undiff right-undiff)

(defrecord Mismatch [- +])
(defrecord Deletion [-])
(defrecord Insertion [+])

(defprotocol Diff
  (-diff-similar [x y]))

;; For property based testing
(defprotocol Undiff
  (-left-undiff [x])
  (-right-undiff [x]))

(defn- shift-insertions [ins]
  (reduce (fn [res idx]
            (let [offset (apply + (map count (vals res)))]
              (assoc res (+ idx offset) (get ins idx))))
          {}
          (sort (keys ins))))

(defn- replacements
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

(defn- del+ins
  "Wrapper around clj-diff that returns deletions and insertions as a set and map
  respectively."
  [exp act]
  (let [{del :- ins :+} (seq-diff/diff exp act)]
    [(into #{} del)
     (into {} (map (fn [[k & vs]] [k (vec vs)])) ins)]))

(defn- diff-seq-replacements [replacements s]
  (map-indexed
   (fn [idx v]
     (if (contains? replacements idx)
       (diff v (get replacements idx))
       v))
   s))

(defn- diff-seq-deletions [del s]
  (map
   (fn [v idx]
     (if (contains? del idx)
       (->Deletion v)
       v))
   s
   (range)))

(defn- diff-seq-insertions [ins s]
  (reduce (fn [res [idx vs]]
            (concat (take (inc idx) res) (map ->Insertion vs) (drop (inc idx) res)))
          s
          ins))

(defn- diff-seq [exp act]
  (let [[rep del ins] (replacements (del+ins exp act))]
    (->> exp
         (diff-seq-replacements rep)
         (diff-seq-deletions del)
         (diff-seq-insertions ins)
         (into []))))

#?(:clj (defn- val-type [val]
          (let [t (type val)]
            (if (class? t)
              (symbol (.getName ^Class t))
              t))))

(defn- diff-map [exp act]
  (first
   (let [exp-ks (keys exp)
         act-ks (concat (filter (set (keys act)) exp-ks)
                        (remove (set exp-ks) (keys act)))
         [del ins] (del+ins exp-ks act-ks)]
     (reduce
      (fn [[m idx] k]
        [(cond-> m
           (contains? del idx)
           (assoc (->Deletion k) (exp k))

           (not (contains? del idx))
           (assoc k (diff (get exp k) (get act k)))

           (contains? ins idx)
           (into (map (juxt ->Insertion (partial get act))) (get ins idx)))
         (inc idx)])
      [(if (contains? ins -1)
         (into {} (map (juxt ->Insertion (partial get act))) (get ins -1))
         {}) 0]
      exp-ks))))

(defn- primitive? [x]
  (or (number? x) (string? x) (boolean? x) (inst? x) (keyword? x) (symbol? x)))

(defn- diff-atom [exp act]
  (if (= exp act)
    exp
    (->Mismatch exp act)))

(defn- diff-similar [x y]
  #?(:clj (-diff-similar x y)
     :cljs
     (if (primitive? x)
       (diff-atom x y)
       (-diff-similar x y))))

(defn- left-undiff [m]
  #?(:clj (-left-undiff m)
     :cljs
     (if (primitive? m)
       m
       (-left-undiff m))))

(defn- right-undiff [m]
  #?(:clj (-right-undiff m)
     :cljs
     (if (primitive? m)
       m
       (-right-undiff m))))

(defn diff [exp act]
  (if (= (data/equality-partition exp) (data/equality-partition act))
    (diff-similar exp act)
    (diff-atom exp act)))

#?(:clj (extend nil
          Diff
          {:-diff-similar diff-atom})
   :cljs (extend-type nil
           Diff
           (-diff-similar [x y] (diff-atom x y))))

#?(:cljs (extend-type js/Object
           Diff
           (-diff-similar [x y] (diff-atom (js->clj x) (js->clj y)))))

#?(:clj (extend Object
          Diff
          {:-diff-similar (fn [exp act]
                            (if (.isArray (.getClass ^Object exp))
                              (diff-seq exp act)
                              (diff-atom exp act)))}))

(extend-protocol Diff
  #?(:clj java.util.List
     :cljs cljs.core/List)
  (-diff-similar [exp act] (diff-seq exp act))

  #?@(:cljs
      [cljs.core/PersistentVector
       (-diff-similar [exp act] (diff-seq exp act))

       cljs.core/EmptyList
       (-diff-similar [exp act] (diff-seq exp act))

       cljs.core/PersistentHashMap
       (-diff-similar [exp act] (diff-map exp act))

       array
       (-diff-similar [exp act] (diff-seq exp act))])

  #?(:clj java.util.Set
     :cljs cljs.core/PersistentHashSet)
  (-diff-similar [exp act]
    (let [exp-seq (seq exp)
          act-seq (seq act)]
      (set (diff-seq exp-seq (concat (filter act exp-seq)
                                     (remove exp act-seq))))))

  #?(:clj java.util.Map
     :cljs cljs.core/PersistentArrayMap)
  (-diff-similar [exp act] (diff-map exp act)))

(extend-protocol Undiff
  #?(:clj java.util.List
     :cljs cljs.core/List)
  (-left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s)))
  (-right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s)))

  #?(:cljs cljs.core/EmptyList)
  #?(:cljs (-left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s))))
  #?(:cljs (-right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s))))

  #?(:cljs cljs.core/PersistentVector)
  #?(:cljs (-left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s))))
  #?(:cljs (-right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s))))

  #?(:clj java.util.Set
     :cljs cljs.core/PersistentHashSet)
  (-left-undiff [s] (set (left-undiff (seq s))))
  (-right-undiff [s] (set (right-undiff (seq s))))

  #?(:cljs cljs.core/KeySeq)
  #?(:cljs (-left-undiff [s] (map left-undiff (remove #(instance? Insertion %) s))))
  #?(:cljs (-right-undiff [s] (map right-undiff (remove #(instance? Deletion %) s))))

  #?(:clj java.util.Map
     :cljs cljs.core/PersistentArrayMap)
  (-left-undiff [m]
    (into {}
          (comp (remove #(instance? Insertion (key %)))
                (map (juxt (comp left-undiff key) (comp left-undiff val))))
          m))
  (-right-undiff [m]
    (into {}
          (comp (remove #(instance? Deletion (key %)))
                (map (juxt (comp right-undiff key) (comp right-undiff val))))
          m))

  #?(:cljs cljs.core/PersistentHashMap)
  #?(:cljs (-left-undiff [m]
    (into {}
          (comp (remove #(instance? Insertion (key %)))
                (map (juxt (comp left-undiff key) (comp left-undiff val))))
          m)))
  #?(:cljs (-right-undiff [m]
    (into {}
          (comp (remove #(instance? Deletion (key %)))
                (map (juxt (comp right-undiff key) (comp right-undiff val))))
          m)))

  Mismatch
  (-left-undiff [m] (get m :-))
  (-right-undiff [m] (get m :+))

  Insertion
  (-right-undiff [m] (get m :+))

  Deletion
  (-left-undiff [m] (get m :-)))

(extend-type nil
  Undiff
  (-left-undiff [m] m)
  (-right-undiff [m] m))

#?(:clj (extend Object Undiff {:-left-undiff identity :-right-undiff identity}))
#?(:cljs (extend-type cljs.core/UUID
  Undiff
  (-left-undiff [m] m)
  (-right-undiff [m] m)))

#?(:cljs (extend-type js/Object
  Undiff
  (-left-undiff [m] m)
  (-right-undiff [m] m)))
