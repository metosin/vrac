(ns example.todolist.event
  (:require [vrac.data :as vd]
            [signaali.reactive :as sr]
            [example.todolist.data :as data]))

;; Problem: We struggle between 2 data types inside the event handlers:
;;          - IDataContainer
;;          - ReactiveNode

;; Event handler
(defn- check-one-todo-as [^vd/IViewData todo-item is-done]
  (reset! (-> todo-item :done) is-done))

;; Event handler
(defn- check-all-todos-as [^vd/IViewData todo-list is-done]
  (doseq [todo-item (-> todo-list :todo-items)]
    (check-one-todo-as todo-item is-done)))

;; Event handler
(defn- delete-todo-item [^vd/IViewData todo-item]
  (let [todo-items (-> todo-item (vd/-get-access-parent))
        ^sr/ReactiveNode reactive-node-to-delete (vd/-get-reactive-data todo-item)]
    ;; TODO: This part is a bit confusing for the user, who need to know the implementation of the store.
    ;;       Find a way to make it simpler, maybe by improving reset and swap on IDataContainer ?
    ;;       The goal would then be to avoid having to mention the reactive nodes anywhere.
    (swap! todo-items (fn [^clj items] ;; items is a collection of reactive nodes
                        (->> items
                             (remove #{reactive-node-to-delete})
                             vec)))
    (swap! (-> data/db :todo-item-by-id) dissoc @(-> todo-item :id))))

;; Event handler
(defn- create-new-todo-item [^vd/IViewData todo-list
                             ^sr/ReactiveNode new-todo-description]
  (let [new-todo-item-id (-> @(-> data/db :todo-item-by-id)
                             keys
                             (->> (reduce max -1))
                             inc)
        new-todo-item (vd/signalify {:id          new-todo-item-id
                                     :done        false
                                     :description @new-todo-description})]
    (swap! (-> todo-list :todo-items) conj new-todo-item)
    (swap! (-> data/db :todo-item-by-id) assoc new-todo-item-id new-todo-item)
    (reset! new-todo-description "")))
