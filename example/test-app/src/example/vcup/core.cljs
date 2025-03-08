(ns example.vcup.core
  (:require [vrac.web :as vw :refer [$]]))

(defn- text-node-article []
  ($ :article
     ($ :h2 "Text nodes")

     ($ :h3 "Text")
     "Hello, " "world!"

     ($ :h3 "Numbers")
     1 " " 2 " " 3

     ($ :h3 "Booleans")
     false " " true

     ;; Because it could be mistaken with the element's attributes, this shall not work.
     ;;($ :h3 "Maps")
     ;;{:a 1
     ;; :b 2}

     ($ :h3 "Keywords")
     :foo " " :bar " " :foo/bar

     ($ :h3 "Vectors")
     [:div {:style {:color "lime"}} 1 2]

     ($ :h3 "`nil` values")
     nil
     nil

     ,))

(defn element-article []
  ($ :article
     ($ :h2 "Vcup elements with funny names")
     ($ :span#id1.class1.class2 {:class [:class3 :class4]} :span#id1.class1.class2)
     ($ :#id2.class3 :#id2.class3)
     ($ :.class4 :.class4)))

(defn fragment-article []
  ($ :article
     ($ :h2 "Fragments")
     ($ :<>
        ($ :<>
           "1a"
           "1b")
        nil
        ($ :<>
           "2a"
           nil
           "2b")
        ($ :<>
           nil
           nil)
        ($ :<>))))

(defn component-with-arguments-article [a b c]
   ($ :article
      ($ :h2 "Component with arguments")
      ($ :pre
         ($ :code
            "(is " (+ a b c) " (+ a b c))"))))

(defn vcup-root []
  ($ :div
     ($ text-node-article)
     ($ element-article)
     ($ fragment-article)
     ($ component-with-arguments-article 100 20 3)))
