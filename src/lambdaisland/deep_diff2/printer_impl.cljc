(ns lambdaisland.deep-diff2.printer-impl
  (:require [arrangement.core]
            [fipp.engine :as fipp]
            [lambdaisland.deep-diff2.diff-impl :as diff]
            [lambdaisland.deep-diff2.puget.color :as color]
            [lambdaisland.deep-diff2.puget.dispatch :as dispatch]
            [lambdaisland.deep-diff2.puget.printer :as puget-printer]
            #?(:cljs [goog.string :refer [format]]))
  #?(:clj
     (:import)))

(defn print-deletion [printer expr]
  (let [no-color (assoc printer :print-color false)]
    (color/document printer ::deletion [:span "-" (puget-printer/format-doc no-color (:- expr))])))

(defn print-insertion [printer expr]
  (let [no-color (assoc printer :print-color false)]
    (color/document printer ::insertion [:span "+" (puget-printer/format-doc no-color (:+ expr))])))

(defn print-mismatch [printer expr]
  [:group
   [:span ""] ;; needed here to make this :nest properly in kaocha.report/print-expr '=
   [:align
    (print-deletion printer expr) :line
    (print-insertion printer expr)]])

(defn print-other [printer expr]
  (let [no-color (assoc printer :print-color false)]
    (color/document printer ::other [:span "-" (puget-printer/format-doc no-color expr)])))

(defn- map-handler [this value]
  (let [ks (#'puget-printer/order-collection (:sort-keys this) value (partial sort-by first arrangement.core/rank))
        entries (map (partial puget-printer/format-doc this) ks)]
    [:group
     (color/document this :delimiter "{")
     [:align (interpose [:span (:map-delimiter this) :line] entries)]
     (color/document this :delimiter "}")]))

(defn- map-entry-handler [printer value]
  (let [k (key value)
        v (val value)]
    (let [no-color (assoc printer :print-color false)]
      (cond
        (instance? lambdaisland.deep_diff2.diff_impl.Insertion k)
        [:span
         (print-insertion printer k)
         (if (coll? v) (:map-coll-separator printer) " ")
         (color/document printer ::insertion (puget-printer/format-doc no-color v))]

        (instance? lambdaisland.deep_diff2.diff_impl.Deletion k)
        [:span
         (print-deletion printer k)
         (if (coll? v) (:map-coll-separator printer) " ")
         (color/document printer ::deletion (puget-printer/format-doc no-color v))]

        :else
        [:span
         (puget-printer/format-doc printer k)
         (if (coll? v) (:map-coll-separator printer) " ")
         (puget-printer/format-doc printer v)]))))

(def print-handlers
  (atom #?(:clj
           {'lambdaisland.deep_diff2.diff_impl.Deletion
            print-deletion

            'lambdaisland.deep_diff2.diff_impl.Insertion
            print-insertion

            'lambdaisland.deep_diff2.diff_impl.Mismatch
            print-mismatch

            'clojure.lang.PersistentArrayMap
            map-handler

            'clojure.lang.PersistentHashMap
            map-handler

            'clojure.lang.MapEntry
            map-entry-handler}

           :cljs
           {'lambdaisland.deep-diff2.diff-impl/Deletion
            print-deletion

            'lambdaisland.deep-diff2.diff-impl/Insertion
            print-insertion

            'lambdaisland.deep-diff2.diff-impl/Mismatch
            print-mismatch

            'cljs.core/PersistentArrayMap
            map-handler

            'cljs.core/PersistentHashMap
            map-handler

            'cljs.core/MapEntry
            map-entry-handler})))

(defn type-name
  "Get the type of the given object as a string. For Clojure, gets the name of
  the class of the object. For ClojureScript, gets either the `name` attribute
  or the protocol name if the `name` attribute doesn't exist."
  [x]
  #?(:bb
     (symbol (str (type x)))
     :clj
     (symbol (.getName (class x)))
     :cljs
     (let [t (type x)
           n (.-name t)]
       (if (empty? n)
         (symbol (pr-str t))
         (symbol n)))))

(defn- print-handler-resolver [extra-handlers]
  (fn [obj]
    (and obj (get (merge @print-handlers extra-handlers)
                  (symbol (type-name obj))))))

(defn register-print-handler!
  "Register an extra print handler.

  `type` must be a symbol of the fully qualified class name. `handler` is a
  Puget handler function of two arguments, `printer` and `value`."
  [type handler]
  (swap! print-handlers assoc type handler))

(defn- color-scheme-mapping
  "Translates user-friendly keys to internal namespaced keys."
  [colors]
  (let [mapping {:lambdaisland.deep-diff2/deletion  ::deletion
                 :lambdaisland.deep-diff2/insertion ::insertion
                 :lambdaisland.deep-diff2/other     ::other}]
    (reduce-kv (fn [m k v]
                 (assoc m (get mapping k k) v)) ;; Fallback to original key if not in mapping
               {}
               colors)))

(defn puget-printer
  ([]
   (puget-printer {}))
  ([opts]
   (let [opts (update opts :color-scheme color-scheme-mapping)
         extra-handlers (:extra-handlers opts)]
     (puget-printer/pretty-printer (puget-printer/merge-options {:width          (or *print-length* 100)
                                                                 :print-color    true
                                                                 :color-scheme   {::deletion  [:red]
                                                                                  ::insertion [:green]
                                                                                  ::other     [:yellow]
                                                            ;; lambdaisland.deep-diff2.puget uses green and red for
                                                            ;; boolean/tag, but we want to reserve
                                                            ;; those for diffed values.
                                                                                  :boolean    [:bold :cyan]
                                                                                  :tag        [:magenta]}
                                                                 :print-handlers  (dispatch/chained-lookup
                                                                                   (print-handler-resolver extra-handlers)
                                                                                   puget-printer/common-handlers)}
                                                                (dissoc opts :extra-handlers))))))

(defn format-doc [expr printer]
  (puget-printer/format-doc printer expr))

(defn print-doc [doc printer]
  (fipp.engine/pprint-document doc {:width (:width printer)}))
