(ns str-rep
  (:require [clojure.string :as str]))

(defn left-pad [s len pad]
  (concat (repeat (- len (count s)) pad) s))

(defn int->hex [i]
  (str/upper-case
   (Integer/toHexString i)))

(defn unicode-rep [char]
  (apply str "\\u" (left-pad (int->hex (long char)) 4 \0)))

(defn char-rep [char]
  (cond
    (= \backspace char)
    "\\b"
    (= \tab char)
    "\\t"
    (= \newline char)
    "\\n"
    (= \formfeed char)
    "\\f"
    (= \return char)
    "\\r"
    (< (long char) 32)
    (unicode-rep char)
    :else
    (str char)))

(defn str-rep [s]
  (str "\""
       (apply str (map char-rep s))
       "\""))
