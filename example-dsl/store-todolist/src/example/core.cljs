(ns example.core
  (:require [example.todolist.view :refer [todolist-root]]
            [signaali.reactive :as sr]
            [vrac.web :as vw :refer [$]]))

(defn- debug-prn [reactive-node event-type]
  (when-some [name (-> reactive-node meta :name)]
    (prn name event-type)))

;; Print the debug info in the Browser's console.
;;(set! sr/*notify-lifecycle-event* debug-prn)

;; Shadow-CLJS hooks: start & reload the app

(defn ^:dev/after-load setup! []
  (vw/render (js/document.getElementById "app")
             ($ todolist-root)))

(defn ^:dev/before-load shutdown! []
  (vw/dispose-render-effects))

(defn start-app []
  (setup!))
