(ns lambdaisland.deep-diff2.puget.printer
  "Enhanced printing functions for rendering Clojure values. The following
  options are available to control the printer:

  #### General Rendering

  `:width`

  Number of characters to try to wrap pretty-printed forms at.

  `:print-meta`

  If true, metadata will be printed before values. Defaults to the value of
  `*print-meta*` if unset.

  #### Collection Options

  `:sort-keys`

  Print maps and sets with ordered keys. If true, the pretty printer will sort
  all unordered collections before printing. If a number, counted collections
  will be sorted if they are smaller than the given size. Otherwise
  collections are printed in their natural sort order. Sorted collections are
  always printed in their natural sort order.

  `:map-delimiter`

  The text placed between key-value pairs in a map.

  `:map-coll-separator`

  The text placed between a map key and a collection value. The keyword :line
  will cause line breaks if the whole map does not fit on a single line.

  `:namespace-maps`

  Extract common keyword namespaces from maps using the namespace map literal
  syntax. See `*print-namespace-maps*`.

  `:seq-limit`

  If set to a positive number, then lists will only render at most the first n
  elements. This can help prevent unintentional realization of infinite lazy
  sequences.

  #### Color Options

  `:print-color`

  When true, ouptut colored text from print functions.

  `:color-markup`

  :ansi for ANSI color text (the default)
  :html-inline for inline-styled html
  :html-classes to use the names of the keys in the :color-scheme map
  as class names for spans so styling can be specified via CSS.

  `:color-scheme`

  Map of syntax element keywords to color codes.

  #### Type Handling

  `:print-handlers`

  A lookup function which will return a rendering function for a given class
  type. This will be tried before the built-in type logic. See the
  `lambdaisland.deep-diff2.puget.dispatch` namespace for some helpful constructors. The returned
  function should accept the current printer and the value to be rendered
  returning a format document.

  `:print-fallback`

  Keyword argument specifying how to format unknown values. Puget supports a few
  different options:

  - `:pretty` renders values with the default colored representation.
  - `:print` defers to the standard print method by rendering unknown values
    using `pr-str`.
  - `:error` will throw an exception when types with no defined handler are
    encountered.
  - A function value will be called with the current printer options and the
    unknown value and is expected to return a formatting document representing
    it.
  "
  (:require [arrangement.core :as order]
            [clojure.string :as str]
            [fipp.engine :as fe]
            [fipp.visit :as fv]
            [lambdaisland.deep-diff2.puget.color :as color]
            [lambdaisland.deep-diff2.puget.color.ansi]
            [lambdaisland.deep-diff2.puget.color.html]
            [lambdaisland.deep-diff2.puget.dispatch :as dispatch]
            #?(:cljs [goog.object :as gobj]))
  (:import #?@(:clj [(java.text SimpleDateFormat)
                     (java.util TimeZone)
                     (java.sql Timestamp)]
               :cljs [(goog.i18n DateTimeFormat)])))

(defn get-type-name
  "Get the type of the given object as a string. For Clojure, gets the name of
  the class of the object. For ClojureScript, gets either the `name` attribute
  or the protocol name if the `name` attribute doesn't exist."
  [x]
  #?(:clj (.getName (class x))
     :cljs (let [t (type x)
                 n (.-name t)]
             (if (empty? n)
               (pr-str t)
               n))))

(defn get-identity-hashcode
  "Get the hashcode for a given object o"
  [o]
  #?(:clj (System/identityHashCode o)
     :cljs (hash o)))

(defn to-hex-string
  "Returns a hex representation of input-string"
  [input-string]
  #?(:clj (Integer/toHexString input-string)
     :cljs (.toString input-string 16)))

;; ## Control Vars
(def ^:dynamic *options*
  "Default options to use when constructing new printers."
  {:width 80
   :sort-keys 80
   :map-delimiter ","
   :map-coll-separator " "
   :namespace-maps false
   :print-fallback :pretty
   :print-color false
   :color-markup :ansi
   :color-scheme
   {;; syntax elements
    :delimiter [:bold :red]
    :tag       [:red]

    ;; primitive values
    :nil       [:bold :black]
    :boolean   [:green]
    :number    [:cyan]
    :string    [:bold :magenta]
    :character [:bold :magenta]
    :keyword   [:bold :yellow]
    :symbol    nil

    ;; special types
    :function-symbol [:bold :blue]
    :class-delimiter [:blue]
    :class-name      [:bold :blue]}})

(defn merge-options
  "Merges maps of printer options, taking care to combine the color scheme
  correctly."
  [a b]
  (let [colors (merge (:color-scheme a) (:color-scheme b))]
    (assoc (merge a b) :color-scheme colors)))

(defmacro with-options
  "Executes the given expressions with a set of options merged into the current
  option map."
  [opts & body]
  `(binding [*options* (merge-options *options* ~opts)]
     ~@body))

(defmacro with-color
  "Executes the given expressions with colored output enabled."
  [& body]
  `(with-options {:print-color true}
     ~@body))

(defn color-text
  "Produces text colored according to the active color scheme. This is mostly
  useful to clients which want to produce output which matches data printed by
  Puget, but which is not directly printed by the library. Note that this
  function still obeys the `:print-color` option."
  ([element text]
   (color-text *options* element text))
  ([options element text]
   (color/text options element text)))

;; ## Formatting Methods
(defn- order-collection
  "Takes a sequence of entries and checks the mode to determine whether to sort
  them. Returns an appropriately ordered sequence."
  [mode coll sort-fn]
  (if (and (not (sorted? coll))
           (or (true? mode)
               (and (number? mode)
                    (counted? coll)
                    (>= mode (count coll)))))
    (sort-fn coll)
    (seq coll)))


(defn- common-key-ns
  "Extract a common namespace from the keys in the map. Returns a tuple of the
  ns string and the stripped map, or nil if the keys are not keywords or there
  is no sufficiently common namespace."
  [m]
  (when (every? (every-pred keyword? namespace) (keys m))
    (let [nsf (frequencies (map namespace (keys m)))
          [common n] (apply max-key val nsf)]
      (when (< (/ (count m) 2) n)
        [common
         (into (empty m)
               (map (fn strip-common
                      [[k v :as e]]
                      (if (= common (namespace k))
                        [(keyword (name k)) v]
                        e)))
               m)]))))

(defn format-unknown
  "Renders common syntax doc for an unknown representation of a value."
  ([printer value]
   (format-unknown printer value (str value)))
  ([printer value repr]
   (format-unknown printer value (get-type-name value) repr))
  ([printer value tag repr]
   (let [sys-id (to-hex-string (get-identity-hashcode value))]
     [:span
      (color/document printer :class-delimiter "#<")
      (color/document printer :class-name tag)
      (color/document printer :class-delimiter "@")
      sys-id
      (when (not= repr (str tag "@" sys-id))
        (list " " repr))
      (color/document printer :class-delimiter ">")])))

(defn format-doc*
  "Formats a document without considering metadata."
  [printer value]
  (let [lookup (:print-handlers printer)
        handler (and lookup (lookup value))]
    (if handler
      (handler printer value)
      (fv/visit* printer value))))

(defn format-doc
  "Recursively renders a print document for the given value."
  [printer value]
  (if-let [metadata (meta value)]
    (fv/visit-meta printer metadata value)
    (format-doc* printer value)))

;; ## Type Handlers
(defn pr-handler
  "Print handler which renders the value with `pr-str`."
  [printer value]
  (pr-str value))

(defn unknown-handler
  "Print handler which renders the value using the printer's unknown type logic."
  [printer value]
  (fv/visit-unknown printer value))

(defn tagged-handler
  "Generates a print handler function which renders a tagged-literal with the
  given tag and a value produced by calling the function."
  [tag value-fn]
  (when-not (symbol? tag)
    (throw (ex-info (str "Cannot create tagged handler with non-symbol tag "
                         (pr-str tag))
                    {:tag tag, :value-fn value-fn})))
  (when-not (ifn? value-fn)
    (throw (ex-info (str "Cannot create tagged handler for " tag
                         " with non-function value transform")
                    {:tag tag, :value-fn value-fn})))
  (fn handler
    [printer value]
    (format-doc printer (tagged-literal tag (value-fn value)))))

(def inst-pattern  "yyyy-MM-dd'T'HH:mm:ss.SSS-00:00")

#?(:cljs
   (defn utc-date [date]
     (js/Date.
      (.getUTCFullYear date)
      (.getUTCMonth date)
      (.getUTCDate date)
      (.getUTCHours date)
      (.getUTCMinutes date)
      (.getUTCSeconds date)
      (.getUTCMilliseconds date))))

#?(:clj
   (defn utc-timestamp-format ^SimpleDateFormat []
     (doto (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss")
       (.setTimeZone (TimeZone/getTimeZone "GMT")))))

(def platform-handlers
  "Map of print handlers for Java/JavaScript types. This supports syntax for regular
  expressions, dates, UUIDs, and futures."
  #?(:clj
     {java.lang.Class
      (fn class-handler
        [printer value]
        (format-unknown printer value "Class" (get-type-name value)))

      java.util.concurrent.Future
      (fn future-handler
        [printer value]
        (let [doc (if (future-done? promise)
                    (format-doc printer @value)
                    (color/document printer :nil "pending"))]
          (format-unknown printer value "Future" doc)))

      java.util.UUID
      (tagged-handler 'uuid str)

      java.util.Date
      (tagged-handler
       'inst
       #(-> (java.text.SimpleDateFormat. inst-pattern)
            (doto (.setTimeZone (java.util.TimeZone/getTimeZone "GMT")))
            (.format ^java.util.Date %)))

      java.util.GregorianCalendar
      (tagged-handler
       'inst
       #(let [formatted (format "%1$tFT%1$tT.%1$tL%1$tz" %)
              offset-minutes (- (.length formatted) 2)]
          (str (subs formatted 0 offset-minutes)
               ":"
               (subs formatted offset-minutes))))

      java.sql.Timestamp
      (tagged-handler
       'inst
       (fn [ts]
         (str (.format ^SimpleDateFormat (utc-timestamp-format) ts)
              (format ".%09d-00:00" (.getNanos ^Timestamp ts)))))}

     :cljs
     {inst?
      (tagged-handler
       'inst
       #(.format (DateTimeFormat. inst-pattern) (utc-date %)))

      uuid?
      (tagged-handler 'uuid str)

      object?
      (tagged-handler
       'js
       (fn [x]
         ;; non-recursive conversion to map
         (reduce (fn [m k]
                   (assoc m k (gobj/get x k)))
                 {}
                 (js/Object.keys x))))}))

(def clojure-handlers
  "Map of print handlers for 'primary' Clojure types. These should take
  precedence over the handlers in `clojure-interface-handlers`."
  {#?(:clj clojure.lang.Atom
      :cljs #(implements? IAtom %))
   (fn atom-handler
     [printer value]
     (format-unknown printer value "Atom" (format-doc printer @value)))
   #?(:clj clojure.lang.Delay
      :cljs #(implements? Delay %))
   (fn delay-handler
     [printer value]
     (let [doc (if (realized? value)
                 (format-doc printer @value)
                 (color/document printer :nil "pending"))]
       (format-unknown printer value "Delay" doc)))
   #?(:clj clojure.lang.ISeq
      :cljs seq?)
   (fn iseq-handler
     [printer value]
     (fv/visit-seq printer value))})

(def clojure-interface-handlers
  "Fallback print handlers for other Clojure interfaces."
  {#?(:clj clojure.lang.IPending
      :cljs #(implements? IPending %))
   (fn pending-handler
     [printer value]
     (let [doc (if (realized? value)
                 (format-doc printer @value)
                 (color/document printer :nil "pending"))]
       (format-unknown printer value doc)))
   #?(:clj clojure.lang.Fn
      :cljs fn?)
   (fn fn-handler
     [printer value]
     (let [doc (let [[vname & tail] (-> (get-type-name value)
                                        (str/replace-first "$" "/")
                                        (str/split #"\$"))]
                 (if (seq tail)
                   (str vname "["
                        (->> tail
                             (map #(first (str/split % #"__")))
                             (str/join "/"))
                        "]")
                   vname))]
       (format-unknown printer value "Fn" doc)))})

(def common-handlers
  "Print handler dispatch combining Java and Clojure handlers with inheritance
  lookups. Provides a similar experience as the standard Clojure
  pretty-printer."
  #?(:clj (dispatch/chained-lookup
           (dispatch/inheritance-lookup platform-handlers)
           (dispatch/inheritance-lookup clojure-handlers)
           (dispatch/inheritance-lookup clojure-interface-handlers))
     :cljs (dispatch/chained-lookup
            (dispatch/predicate-lookup platform-handlers)
            (dispatch/predicate-lookup clojure-handlers)
            (dispatch/predicate-lookup clojure-interface-handlers))))


;; ## Canonical Printer Implementation
(defrecord CanonicalPrinter [print-handlers]
  fv/IVisitor

  ;; Primitive Types
  (visit-nil
    [this]
    "nil")

  (visit-boolean
    [this value]
    (str value))

  (visit-number
    [this value]
    (pr-str value))

  (visit-character
    [this value]
    (pr-str value))

  (visit-string
    [this value]
    (pr-str value))

  (visit-keyword
    [this value]
    (str value))

  (visit-symbol
    [this value]
    (str value))

  ;; Collection Types
  (visit-seq
    [this value]
    (if (seq value)
      (let [entries (map (partial format-doc this) value)]
        [:group "(" [:align (interpose " " entries)] ")"])
      "()"))

  (visit-vector
    [this value]
    (if (seq value)
      (let [entries (map (partial format-doc this) value)]
        [:group "[" [:align (interpose " " entries)] "]"])
      "[]"))

  (visit-set
    [this value]
    (if (seq value)
      (let [entries (map (partial format-doc this)
                         (sort order/rank value))]
        [:group "#{" [:align (interpose " " entries)] "}"])
      "#{}"))

  (visit-map
    [this value]
    (if (seq value)
      (let [entries (map #(vector :span (format-doc this (key %))
                                  " "   (format-doc this (val %)))
                         (sort-by first order/rank value))]
        [:group "{" [:align (interpose " " entries)] "}"])
      "{}"))

  ;; Clojure Types
  (visit-meta
    [this metadata value]
    ;; Metadata is not printed for canonical rendering.
    (format-doc* this value))

  (visit-var
    [this value]
    ;; Defer to unknown, cover with handler.
    (fv/visit-unknown this value))

  (visit-pattern
    [this value]
    ;; Defer to unknown, cover with handler.
    (fv/visit-unknown this value))

  (visit-record
    [this value]
    ;; Defer to unknown, cover with handler.
    (fv/visit-unknown this value))

  ;; Special Types
  (visit-tagged
    [this value]
    [:span (str "#" (:tag value)) " " (format-doc this (:form value))])

  (visit-unknown
    [this value]
    (let [not-defined-representation-message (str "No defined representation for "
                                                  (get-type-name value)
                                                  ": "
                                                  (pr-str value))]
      (throw (ex-info not-defined-representation-message
                      {:causes #{:undefined-representation}})))))

(defn canonical-printer
  "Constructs a new canonical printer with the given handler dispatch."
  ([]
   (canonical-printer nil))
  ([handlers]
   (assoc (CanonicalPrinter. handlers)
          :width 0)))

;; Remove automatic constructor function.
#?(:clj (ns-unmap *ns* '->CanonicalPrinter))

;; ## Pretty Printer Implementation
(defrecord PrettyPrinter

    [width
     print-meta
     sort-keys
     map-delimiter
     map-coll-separator
     namespace-maps
     seq-limit
     print-color
     color-markup
     color-scheme
     print-handlers
     print-fallback]

  fv/IVisitor

  ;; Primitive Types
  (visit-nil
    [this]
    (color/document this :nil "nil"))

  (visit-boolean
    [this value]
    (color/document this :boolean (str value)))

  (visit-number
    [this value]
    (color/document this :number (pr-str value)))

  (visit-character
    [this value]
    (color/document this :character (pr-str value)))

  (visit-string
    [this value]
    (color/document this :string (pr-str value)))

  (visit-keyword
    [this value]
    (color/document this :keyword (str value)))

  (visit-symbol
    [this value]
    (color/document this :symbol (str value)))

  ;; Collection Types
  (visit-seq
    [this value]
    (if (seq value)
      (let [[values trimmed?]
            (if (and seq-limit (pos? seq-limit))
              (let [head (take seq-limit value)]
                [head (<= seq-limit (count head))])
              [(seq value) false])
            elements
            (cond-> (if (symbol? (first values))
                      (cons (color/document this :function-symbol (str (first values)))
                            (map (partial format-doc this) (rest values)))
                      (map (partial format-doc this) values))
              trimmed? (concat [(color/document this :nil "...")]))]
        [:group
         (color/document this :delimiter "(")
         [:align (interpose :line elements)]
         (color/document this :delimiter ")")])
      (color/document this :delimiter "()")))

  (visit-vector
    [this value]
    (if (seq value)
      [:group
       (color/document this :delimiter "[")
       [:align (interpose :line (map (partial format-doc this) value))]
       (color/document this :delimiter "]")]
      (color/document this :delimiter "[]")))

  (visit-set
    [this value]
    (if (seq value)
      (let [entries (order-collection sort-keys value (partial sort order/rank))]
        [:group
         (color/document this :delimiter "#{")
         [:align (interpose :line (map (partial format-doc this) entries))]
         (color/document this :delimiter "}")])
      (color/document this :delimiter "#{}")))

  (visit-map
    [this value]
    (if (seq value)
      (let [[common-ns stripped] (when namespace-maps (common-key-ns value))
            kvs (order-collection sort-keys
                                  (or stripped value)
                                  (partial sort-by first order/rank))
            entries (map (fn [[k v]]
                           [:span
                            (format-doc this k)
                            (if (coll? v)
                              map-coll-separator
                              " ")
                            (format-doc this v)])
                         kvs)
            map-doc [:group
                     (color/document this :delimiter "{")
                     [:align (interpose [:span map-delimiter :line] entries)]
                     (color/document this :delimiter "}")]]
        (if common-ns
          [:group (color/document this :tag (str "#:" common-ns)) :line map-doc]
          map-doc))
      (color/document this :delimiter "{}")))

  ;; Clojure Types
  (visit-meta
    [this metadata value]
    (if print-meta
      [:align
       [:span (color/document this :delimiter "^") (format-doc this metadata)]
       :line (format-doc* this value)]
      (format-doc* this value)))

  (visit-var
    [this value]
    [:span
     (color/document this :delimiter "#'")
     (color/document this :symbol (subs (str value) 2))])

  (visit-pattern
    [this value]
    [:span
     (color/document this :delimiter "#")
     (color/document this :string (str \" value \"))])

  (visit-record
    [this value]
    (fv/visit-tagged
     this
     (tagged-literal (symbol (get-type-name value))
                     (into {} value))))

  ;; Special Types
  (visit-tagged
    [this value]
    (let [{:keys [tag form]} value]
      [:group
       (color/document this :tag (str "#" (:tag value)))
       (if (coll? form) :line " ")
       (format-doc this (:form value))]))

  (visit-unknown
    [this value]
    (case print-fallback
      :pretty
      (format-unknown this value)

      :print
      [:span (pr-str value)]

      :error
      (throw (ex-info (str "No defined representation for " (get-type-name value) ": " (pr-str value))
                      {:causes #{:undefined-representation}}))
      (if (ifn? print-fallback)
        (print-fallback this value)
        (throw (ex-info (str "Unsupported value for print-fallback: " (pr-str print-fallback))
                        {:causes #{:unsupported-value}}))))))

(defn pretty-printer
  "Constructs a new printer from the given configuration."
  [opts]
  (->> [{:print-meta *print-meta*
         :print-handlers common-handlers}
        *options*
        opts]
       (reduce merge-options)
       (map->PrettyPrinter)))

;; Remove automatic constructor function.
#?(:clj (ns-unmap *ns* '->PrettyPrinter))

;; ## Printing Functions
(defn render-out
  "Prints a value using the given printer."
  ([printer value]
   (render-out printer value nil))
  ([printer value opts]
   (binding [*print-meta* false]
     (fe/pprint-document
      (format-doc printer value)
      (merge {:width (:width printer)}
             opts)))))

(defn render-str
  "Renders a value to a string using the given printer."
  ^String
  [printer value]
  (str/trim-newline
   (with-out-str
     (render-out printer value))))

(defn pprint
  "Pretty-prints a value to *out*. Options may be passed to override the
  default *options* map."
  ([value]
   (pprint value nil))
  ([value opts]
   (render-out (pretty-printer opts) value opts)))

(defn pprint-str
  "Pretty-print a value to a string."
  ([value]
   (pprint-str value nil))
  ([value opts]
   (render-str (pretty-printer opts) value)))

(defn cprint
  "Like pprint, but turns on colored output."
  ([value]
   (cprint value nil))
  ([value opts]
   (pprint value (assoc opts :print-color true))))

(defn cprint-str
  "Pretty-prints a value to a colored string."
  ([value]
   (cprint-str value nil))
  ([value opts]
   (pprint-str value (assoc opts :print-color true))))
