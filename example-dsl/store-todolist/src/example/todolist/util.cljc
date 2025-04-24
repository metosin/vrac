(ns example.todolist.util
  (:require [clojure.string :as str]))

(defn random-color-str []
  (str "rgb("
       (->> (repeatedly 3 #(rand-int 256))
            (str/join ","))
       ")"))

(defn random-color-style []
  {:color (random-color-str)
   :background (random-color-str)})
