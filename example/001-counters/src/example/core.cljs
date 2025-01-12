(ns example.core
  (:require [signaali.reactive :as sr]
            [vrac.web :as vw :refer [$]]))

(defn my-counter [counter-state]
  ($ :div "Counter value:" counter-state
     ($ :div
        ($ :button {:on-click #(swap! counter-state inc)} "Increment")
        ($ :button {:on-click #(reset! counter-state 0)} "Reset"))))

(defn my-html [nb-counters]
  ($ :main
     ($ :h1 "Developing Vrac from scratch - components")
     ($ :p "This demonstrates how Vrac is implemented.")
     ($ :h2 "Reactive counters")
     (for [i (range nb-counters)]
       (let [counter-state (sr/create-state (* i 100))]
         (my-counter counter-state)))
     ($ :h2 "Lazy effects")
     ($ :div "The effects are lazy, the user decides when to re-run them. Click the button to update the DOM."
        ($ :div
           ($ :button {:on-click #(sr/re-run-stale-effectful-nodes)} "Run effects")))))

;; Shadow-CLJS hooks: start & reload the app

(defn ^:dev/after-load setup! []
  (vw/render (js/document.getElementById "app")
             (my-html 3)))

(defn ^:dev/before-load shutdown! []
  (vw/dispose-render-effects))

(defn start-app []
  (setup!))
