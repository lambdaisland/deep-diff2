(ns lambdaisland.deep-diff2.puget.color.html
  "Coloring implementation that wraps text in HTML tags to apply color.

  Supports the following modes for `:color-markup`:

  - `:html-inline` applies inline `style` attributes to the tags.
  - `:html-classes` adds semantic `class` attributes to the tags."
  (:require
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [lambdaisland.deep-diff2.puget.color :as color]))

(def style-attribute
  "Map from keywords usable in a color-scheme value to vectors
  representing css style attributes"
  {:none       nil
   :bold       [:font-weight "bold"]
   :underline  [:text-decoration "underline"]
   :blink      [:text-decoration "blink"]
   :reverse    nil
   :hidden     [:visibility "hidden"]
   :strike     [:text-decoration "line-through"]
   :black      [:color "black"]
   :red        [:color "red"]
   :green      [:color "green"]
   :yellow     [:color "yellow"]
   :blue       [:color "blue"]
   :magenta    [:color "magenta"]
   :cyan       [:color "cyan"]
   :white      [:color "white"]
   :fg-256     nil
   :fg-reset   nil
   :bg-black   [:background-color "black"]
   :bg-red     [:background-color "red"]
   :bg-green   [:background-color "green"]
   :bg-yellow  [:background-color "yellow"]
   :bg-blue    [:background-color "blue"]
   :bg-magenta [:background-color "magenta"]
   :bg-cyan    [:background-color "cyan"]
   :bg-white   [:background-color "white"]
   :bg-256     nil
   :bg-reset   nil})

(defn style
  "Returns a formatted style attribute for a span given a seq of
  keywords usable in a :color-scheme value"
  [codes]
  (let [attributes (map #(get style-attribute % [:color (name %)]) codes)]
    (str "style=\""
         (str/join ";" (map (fn [[k v]] (str (name k) ":" v)) attributes))
         "\"")))

(defn escape-html-text
  "Escapes special characters into HTML entities."
  [text]
  (str/escape text {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;"}))

(defn escape-html-node
  "Applies HTML escaping to the node if it is a string. Returns a print
  document representing the escaped string, or the original node if not."
  [node]
  (if (string? node)
    (let [escaped-text (escape-html-text node)
          spans (str/split escaped-text #"(?=&)")]
      (reduce (fn [acc span]
                (case (first span)
                  nil acc
                  \& (let [[escaped span] (str/split span #"(?<=;)" 2)
                           acc (conj acc [:escaped escaped])]
                       (if (seq span)
                         (conj acc span)
                         acc))
                  (conj acc span)))
              [:span]
              spans))
    node))

(defn escape-html-document
  "Escapes special characters into fipp :span/:escaped nodes"
  [document]
  (postwalk escape-html-node document))

(defmethod color/document :html-inline
  [options element document]
  (if-let [codes (-> options :color-scheme (get element) seq)]
    [:span [:pass "<span " (style codes) ">"]
     (escape-html-document document)
     [:pass "</span>"]]
    (escape-html-document document)))

(defmethod color/text :html-inline
  [options element text]
  (if-let [codes (-> options :color-scheme (get element) seq)]
    (str "<span " (style codes) ">" (escape-html-text text) "</span>")
    (escape-html-text text)))

(defmethod color/document :html-classes
  [options element document]
  [:span [:pass "<span class=\"" (name element) "\">"]
   (escape-html-document document)
   [:pass "</span>"]])

(defmethod color/text :html-classes
  [options element text]
  (str "<span class=\"" (name element) "\">" (escape-html-text text) "</span>"))
