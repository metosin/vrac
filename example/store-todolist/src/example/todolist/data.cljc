(ns example.todolist.data
  (:require [vrac.data :as vd]))

(def initial-store-data
  {:todo-item-by-id {0 {:id 0 :done false :description "Clean the cat's litter"}
                     1 {:id 1 :done false :description "Prepare some coffee"}
                     2 {:id 2 :done false :description "Start a REPL"}
                     3 {:id 3 :done true  :description "Make this sample work with a normalized store"}}
   :todo-list-by-id {0 {:id 0
                        :name "Default todo-list"
                        :todo-items [^:ref [:todo-item-by-id 0]
                                     ^:ref [:todo-item-by-id 1]
                                     ^:ref [:todo-item-by-id 2]
                                     ^:ref [:todo-item-by-id 3]]}}})

(def global
  (doto (vd/make-root-view-data)
    (vd/-mount-view-data! :db (vd/fine-grained-store-view-data-factory initial-store-data))
    #_(vd/-mount-view-data! :db (vd/reframe-store-view-data-factory initial-store-data))
    ,))

(def db
  (-> global :db))
