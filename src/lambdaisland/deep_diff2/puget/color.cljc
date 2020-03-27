(ns lambdaisland.deep-diff2.puget.color
  "Coloring multimethods to format text by adding markup.

  #### Color Options

  `:print-color`

  When true, ouptut colored text from print functions.

  `:color-markup`

  - `:ansi` for color terminal text (default)
  - `:html-inline` for inline-styled html
  - `:html-classes` for html with semantic classes

  `:color-scheme`

  Map of syntax element keywords to color codes.
  ")

;; ## Coloring Multimethods
(defn dispatch
  "Dispatches to coloring multimethods. Element should be a key from
  the color-scheme map."
  [options element text]
  (when (:print-color options)
    (:color-markup options)))

(defmulti document
  "Constructs a pretty print document, which may be colored if
  `:print-color` is true."
  #'dispatch)

(defmulti text
  "Produces text colored according to the active color scheme. This is mostly
  useful to clients which want to produce output which matches data printed by
  Puget, but which is not directly printed by the library. Note that this
  function still obeys the `:print-color` option."
  #'dispatch)

;; ## Default Markup
;; The default transformation when there's no markup specified is to return the
;; text unaltered.
(defmethod document nil
  [options element text]
  text)

(defmethod text nil
  [options element text]
  text)
