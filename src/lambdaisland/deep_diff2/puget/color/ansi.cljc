(ns lambdaisland.deep-diff2.puget.color.ansi
  "Coloring implementation that applies ANSI color codes to text designed to be
  output to a terminal.

  Use with a `:color-markup` of `:ansi`."
  (:require
    [clojure.string :as str]
    [lambdaisland.deep-diff2.puget.color :as color]))

(def sgr-code
  "Map of symbols to numeric SGR (select graphic rendition) codes."
  {:none        0
   :bold        1
   :underline   3
   :blink       5
   :reverse     7
   :hidden      8
   :strike      9
   :black      30
   :red        31
   :green      32
   :yellow     33
   :blue       34
   :magenta    35
   :cyan       36
   :white      37
   :fg-256     38
   :fg-reset   39
   :bg-black   40
   :bg-red     41
   :bg-green   42
   :bg-yellow  43
   :bg-blue    44
   :bg-magenta 45
   :bg-cyan    46
   :bg-white   47
   :bg-256     48
   :bg-reset   49})

(defn esc
  "Returns an ANSI escope string which will apply the given collection of SGR
  codes."
  [codes]
  (let [codes (map sgr-code codes codes)
        codes (str/join \; codes)]
    (str \u001b \[ codes \m)))

(defn escape
  "Returns an ANSI escope string which will enact the given SGR codes."
  [& codes]
  (esc codes))

(defn sgr
  "Wraps the given string with SGR escapes to apply the given codes, then reset
  the graphics."
  [string & codes]
  (str (esc codes) string (escape :none)))

(defn strip
  "Removes color codes from the given string."
  [string]
  (str/replace string #"\u001b\[[0-9;]*[mK]" ""))

(defmethod color/document :ansi
  [options element document]
  (if-let [codes (-> options :color-scheme (get element) seq)]
    [:span [:pass (esc codes)] document [:pass (escape :none)]]
    document))

(defmethod color/text :ansi
  [options element text]
  (if-let [codes (-> options :color-scheme (get element) seq)]
    (str (esc codes) text (escape :none))
    text))
