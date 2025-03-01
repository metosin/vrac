(ns example.core
  (:require [example.counter.core :refer [counter-root]]
            [example.vcup.core :refer [vcup-root]]
            [vrac.web :as vw :refer [$]]))

(defn root-component []
  ($ :main
     ($ vcup-root)
     ($ counter-root)))

;; Shadow-CLJS hooks: start & reload the app

(defn ^:dev/after-load setup! []
  (vw/render (js/document.getElementById "app")
             ($ root-component)))

(defn ^:dev/before-load shutdown! []
  (vw/dispose-render-effects))

(defn start-app []
  (setup!))
