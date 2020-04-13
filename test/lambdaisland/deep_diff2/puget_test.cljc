(ns lambdaisland.deep-diff2.puget-test
  (:require [clojure.test :refer [deftest testing is]]
            [lambdaisland.deep-diff2.puget.color.html :as sut]))

(deftest puget-html-test
  (testing "properly escape html"
    (let [input ["<ul id=someList><li class=red>Item 1</li><li>Item 2</li></ul>"]
          expected-result  [[:span
                             [:escaped "&lt;"] "ul id=someList" [:escaped "&gt;"]
                             [:escaped "&lt;"] "li class=red" [:escaped "&gt;"]
                             "Item 1"
                             [:escaped "&lt;"] "/li" [:escaped "&gt;"]
                             [:escaped "&lt;"] "li" [:escaped "&gt;"]
                             "Item 2"
                             [:escaped "&lt;"] "/li" [:escaped "&gt;"]
                             [:escaped "&lt;"] "/ul" [:escaped "&gt;"]]]]
      (is (= expected-result (sut/escape-html-document input))))))

