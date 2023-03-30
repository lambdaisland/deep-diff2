(ns repl-sessions.poke
  (:require [lambdaisland.deep-diff2 :as ddiff]))

(seq #{{:foo 1M} {:bar 2}}) ;; => ({:foo 1M} {:bar 2})
(seq #{{:foo 1} {:bar 2}})  ;; => ({:bar 2} {:foo 1})

(def d1 {{:foo 1M} {:bar 2}})
(def d2 {{:foo 1} {:bar 2}})
(ddiff/pretty-print (ddiff/diff d1 d2))
;; #{+{:foo 1} -{:foo 1M} {:bar 2}}

(def d1 #{{:foo 1M}})
(def d2 #{{:foo 1}})
(ddiff/pretty-print (ddiff/diff d1 d2))

(-> (ddiff/diff {:a "apple" :b "pear"} {:a "apple" :b "banana"})
    ddiff/minimize
    ddiff/pretty-print)
;; {:b -"pear" +"banana"}

;; {:b -2 +3}

[#{1.1197369622161879e-14 1.3019822841584656e-21 0.6875
   #uuid "a907a7fe-d2eb-482d-b1cc-3acfc12daf55"
   -30
   :X/*!1:3
   :u7*A/p?2IG5d*!Nl
   :**d7ws
   "ý"
   "ÔB*àñS�¬ÚûV¡ç�¯±·á£H�
                            �û?'V$ëY;CL�k-oOV"
   !U-h_C*A7/x0_n1
   A-*wn./o_?4w18-!
   "ìêÜ¼à4�^¤mÐðkt�ê1_ò�· À�4\n@J\"2�9)cd-\t®"
   y3W-2
   #uuid "6d507164-f8b9-401d-8c44-d6b0e310c248"
   "M"
   :cy7-3
   :w4/R.-s?9V5
   #uuid "1bcb00c9-88b9-4eae-9fea-60600dfaefa0"
   -20
   #uuid "269ab6f9-f19d-4c9d-a0cb-51150e52e9f7"
   -235024979
   :O:m_9.9+A/N+usPa6.HA*G
   228944.657438457
   :x/w?
   :__+o+sut9!t/?0l
   "�â��«"
   false
   #uuid "b6295f83-8176-47b5-946e-466f74226629"
   e3zQ!E*5
   :T5rb
   :++y:2
   -7364
   zG/ex23
   "¡"
   -4318364480
   :D+?2?!/Hrc!jA7z_2
   :z-I/!8Uq+d?
   -0.5588235294117647
   -0.5925925925925926
   -0.8108108108108109}]
