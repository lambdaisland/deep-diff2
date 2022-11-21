(ns repl-sessions.custom-types
  (:require [lambdaisland.deep-diff2 :as ddiff]))

;; Demonstration of how to set up print handlers for custom types.

;; New custom type for reprsenting "degrees" amount, like Celcius or Fahrenheit.
;; Using `deftype` and not `defrecord` because we handle `defrecord` instances
;; already as if they are maps.

(deftype Degrees [amount unit]
  Object
  (equals [this that] ; needed for proper diffing
    (and (instance? Degrees that)
         (= amount (.-amount that))
         (= unit (.-unit that)))))

;; No custom handler yet, defaults to Object#toString rendering:

(pr-str (->Degrees 10 "C"))
;; => "#object[custom_types.Degrees 0x75634af8 \"custom_types.Degrees@75634af8\"]"

;; And so does ddiff
(ddiff/pretty-print (ddiff/diff [(->Degrees 20 \C)]
                                [(->Degrees 80 \F)]))
;; =>
;; [-#object[custom_types.Degrees 0x660a955 "custom_types.Degrees@660a955"]
;;  +#object[custom_types.Degrees 0x40241557 "custom_types.Degrees@40241557"]]


;; Now we set up a custom handler

(defmethod print-method Degrees [degrees out]
  (.write out (str (.-amount degrees) "°" (.-unit degrees))))


(pr-str (->Degrees 10 "C"))
;; => "10°C"

(ddiff/pretty-print (ddiff/diff [(->Degrees 20 \C)]
                                [(->Degrees 20 \C)
                                 (->Degrees 80 \F)]))
;; => [-20°C +80°F]

;; Add Puget handler, to tap into Puget's rich rendering. Will take precedence
;; over `print-method`.

(lambdaisland.deep-diff2.printer-impl/register-print-handler!
 `Degrees
 (fn [printer value]
   [:span
    (lambdaisland.deep-diff2.puget.color/document printer :number (str (.-amount value)))
    (lambdaisland.deep-diff2.puget.color/document printer :tag "°")
    (lambdaisland.deep-diff2.puget.color/document printer :keyword (str (.-unit value)))]))

(ddiff/pretty-print (->Degrees 20 \C))
;; => 20°C (printed with specific colors)

(ddiff/pretty-print (->Degrees 20 \C) (ddiff/printer {:color-markup :html-inline}))
;;=> <span style="color:cyan">20</span><span style="color:magenta">°</span><span style="font-weight:bold;color:yellow">C</span>
