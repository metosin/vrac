(ns example.todolist.view
  (:require [clojure.string :as str]
            [signaali.reactive :as sr]
            [vrac.data :as vd]
            [vrac.web :as vw :refer [$]]
            [example.todolist.data :as data]
            [example.todolist.event :as event]
            [example.todolist.util :as util]))

;; Problem: We struggle between 2 data types inside the views:
;;          - ReactiveRef,
;;          - ReactiveNode.

;; View component
#_
(defn- todo-item-component [^vd/ReactiveRef todo-item]
  (let [id (-> todo-item :id)
        done (-> todo-item :done)
        description (-> todo-item :description)]
    ($ :li {:style (util/random-color-style)}
       "[id: " (sr/create-derived (fn [] @@id)) "] "
       ($ :input {:type "checkbox"
                  :on/change (fn [^js event]
                               (event/check-one-todo-as @todo-item (-> event .-target .-checked)))}
               (vw/props-effect (fn []
                                  {:checked @@done})))
       ($ :span (vw/props-effect (fn []
                                   (when @@done
                                     {:style {:text-decoration-line "line-through"}})))
          (sr/create-derived (fn [] @@description)))
          ;;($ :br)
          ;;(str (-> description (get-access-path)))]
       " "
       ($ :button {:on/click (fn [^js _event]
                               (event/delete-todo-item @todo-item))}
          "delete"))))

(defn- todo-item-component [todo-item]
  (let [{:keys [id done description]} todo-item]
    ($ :li {:style (util/random-color-style)}
       "[id: " id "] "
       ($ :input {:type "checkbox"
                  :on/change (fn [^js event]
                               (event/check-one-todo-as @todo-item (-> event .-target .-checked)))
                  :checked done})
       ($ :span {:style (when done {:text-decoration-line "line-through"})}
          description)
       " "
       ($ :button {:on/click (fn [^js _event]
                               (event/delete-todo-item @todo-item))}
          "delete"))))

;; View component
#_
(defn- todo-list-component [^vd/ReactiveRef todo-list]
  (let [todo-list-name (-> todo-list :name)
        todo-items (-> todo-list :todo-items)
        all-todos-are-done (sr/create-memo
                             (fn []
                               (->> @todo-items
                                    (every? (fn [^vd/IViewData todo-item]
                                              (-> todo-item :done deref))))))
        only-some-todos-are-done (sr/create-memo
                                   (fn []
                                     (and (not @all-todos-are-done)
                                          (not (->> @todo-items
                                                    (every? (fn [^vd/IViewData todo-item]
                                                              (not (-> todo-item :done deref)))))))))]
    ($ :<>
       ($ :h2 (sr/create-derived (fn [] @@todo-list-name)))

       ;; Todo-list completion indicator
       ($ :div
          ($ :input {:type "checkbox"
                     :on/change (fn [^js event]
                                  (event/check-all-todos-as @todo-list (-> event .-target .-checked)))}
                  (vw/props-effect (fn []
                                     {:checked @all-todos-are-done
                                      :indeterminate @only-some-todos-are-done})))
          (sr/create-derived (fn []
                               (cond
                                 @all-todos-are-done "All todo-items are done."
                                 @only-some-todos-are-done "Only some todo-items are done."
                                 :else "No todo-items are done."))))

       ;; The todo-items
       ($ :ul
          (for-fragment-vector todo-items
                               (fn [_index todo-item]
                                 ($ todo-item-component todo-item))))

       ;; New todo-item creation
       (let [new-todo-description (sr/create-state "")
             new-todo-description-is-blank (sr/create-memo (fn [] (str/blank? @new-todo-description)))]
         ($ :div
            ($ :input {:type "text"
                       :on/input (fn [^js event]
                                   (reset! new-todo-description (-> event .-target .-value)))
                       :on/keydown (fn [^js event]
                                     (when (= (-> event .-keyCode) 13)
                                       (when-not @new-todo-description-is-blank
                                         (event/create-new-todo-item @todo-list new-todo-description))))}
                    (vw/props-effect (fn [] {:value @new-todo-description})))
            " "
            ($ :button {:on/click (fn [^js _event]
                                    (event/create-new-todo-item @todo-list new-todo-description))}
                     (vw/props-effect (fn [] {:disabled @new-todo-description-is-blank}))
               "Add todo-item"))))))

'(defn- todo-list-component [todo-list]
   (let [todo-list-name (-> todo-list :name)
         todo-items (-> todo-list :todo-items)
         all-todos-are-done (are-all-done todo-items)
         all-todos-are-not-done (are-all-not-done todo-items)
         only-some-todos-are-done (and (not all-todos-are-done)
                                       (not all-todos-are-not-done))]
     ($ :<>
        ($ :h2 todo-list-name)

        ;; Todo-list completion indicator
        ($ :div
           ($ :input {:type "checkbox"
                      :on/change (fn [^js event]
                                   (event/check-all-todos-as @todo-list (-> event .-target .-checked)))
                      :checked all-todos-are-done
                      :indeterminate only-some-todos-are-done}
              (cond
                all-todos-are-done "All todo-items are done."
                only-some-todos-are-done "Only some todo-items are done."
                :else "No todo-items are done.")))

        ;; The todo-items
        ($ :ul
           (for [todo-item todo-items]
             ($ todo-item-component todo-item)))

        ;; New todo-item creation
        (let [new-todo-description (state "")
              new-todo-description-is-blank (memo (str/blank? new-todo-description))]
          ($ :div
             ($ :input {:type "text"
                        :on/input (fn [^js event]
                                    (reset! new-todo-description (-> event .-target .-value)))
                        :on/keydown (fn [^js event]
                                      (when (= (-> event .-keyCode) 13)
                                        (when-not @new-todo-description-is-blank
                                          (event/create-new-todo-item @todo-list new-todo-description))))
                        :value new-todo-description})
             " "
             ($ :button {:on/click (fn [^js _event]
                                     (event/create-new-todo-item @todo-list new-todo-description))
                         :disabled new-todo-description-is-blank}
                "Add todo-item"))))))

;; View component
#_
(defn todolist-root []
  (let [global-ref (vd/reactive-ref data/global)]
    ($ :<>
       ($ :h1 "Todo-list sample")
       (for-fragment-map (-> global-ref :db :todo-list-by-id)
                         (fn [_todo-list-id todo-list]
                           ($ todo-list-component todo-list))))))

'(defn todolist-root []
   ($ :<>
      ($ :h1 "Todo-list sample")
      (for [[todo-list-id todo-list] (-> global :db :todo-list-by-id)]
        ($ todo-list-component todo-list))))
