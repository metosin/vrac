(ns example.core
  (:require [example.context :refer [context-root]]
            [example.math-ml :refer [math-ml-root]]
            [example.reactive-data :refer [reactive-data-root]]
            [example.reactive-fragment :refer [reactive-fragment-root]]
            [example.svg :refer [svg-root]]
            [example.ref :refer [ref-root]]
            [example.vcup :refer [vcup-root]]
            [example.ui-component :refer [ui-component-root]]
            [signaali.reactive :as sr]
            [vrac.web :as vw :refer [$]]))

(defn- debug-prn [reactive-node event-type]
  (when-some [name (-> reactive-node meta :name)]
    (prn name event-type)))

;; Print the debug info in the Browser's console.
;;(set! sr/*notify-lifecycle-event* debug-prn)

(defn root-component []
  ($ :main
     ($ vcup-root)
     ($ reactive-data-root)
     ($ reactive-fragment-root)
     ($ ref-root)
     ($ context-root)
     ($ svg-root)
     ($ math-ml-root)
     ($ ui-component-root)
     ,))

;; Shadow-CLJS hooks: start & reload the app

(defn ^:dev/after-load setup! []
  (vw/render (js/document.getElementById "app")
             ($ root-component)))

(defn ^:dev/before-load shutdown! []
  (vw/dispose-render-effects))

(defn start-app []
  (setup!))
