(ns vrac.web
  #?(:cljs (:require-macros [vrac.web :refer [with-context
                                              with-context-update
                                              if-fragment
                                              case-fragment
                                              cond-fragment]]))
  (:require [clojure.string :as str]
            #?(:cljs [goog.object :as gobj])
            [signaali.reactive :as sr]))

;; ----------------------------------------------

(def xmlns-math-ml "http://www.w3.org/1998/Math/MathML")
(def xmlns-html    "http://www.w3.org/1999/xhtml")
(def xmlns-svg     "http://www.w3.org/2000/svg")

#_(def xmlns-by-kw
    {:math xmlns-math-ml
     :html xmlns-html
     :svg  xmlns-svg})

(def ^:private ^:dynamic *xmlns-kw* :none)

;; ----------------------------------------------

(def ^:private ^:dynamic *userland-context*)

(defn get-context []
  *userland-context*)

(defmacro with-context [new-context vcup]
  #?(:clj
     `(binding [*userland-context* ~new-context]
        (process-vcup ~vcup))))

(defmacro with-context-update [context-fn vcup]
  #?(:clj
     `(let [parent-context# *userland-context*
            new-context# (sr/create-derived (fn [] (~context-fn parent-context#)))]
        (with-context new-context# ~vcup))))

;; ----------------------------------------------

(defrecord VcupNode [node-type children])
(defrecord ReactiveFragment [reactive-node])
(defrecord PropEffect [reactive+-props])
(defrecord ComponentResult [effects elements])

;; ----------------------------------------------

#?(:cljs
   (defn- dom-node? [x]
     (instance? js/Node x)))

(defn- vcup-fragment? [x]
  (and (instance? VcupNode x)
       (= (:node-type x) :<>)))

#?(:cljs
   (defn- vcup-element? [x]
     (and (instance? VcupNode x)
          (not= (:node-type x) :<>)
          (or (simple-keyword? (:node-type x))
              (instance? js/Element (:node-type x))))))

(defn- component-invocation? [x]
  (and (instance? VcupNode x)
       (fn? (:node-type x))))

(defn- reactive-fragment? [x]
  (instance? ReactiveFragment x))

(defn- prop-effect? [x]
  (instance? PropEffect x))

(defn- component-result? [x]
  (instance? ComponentResult x))

(defn- reactive-node? [x]
  (instance? signaali.reactive.ReactiveNode x))

(defn- prop-map? [x]
  (and (map? x)
       (not (record? x))))

(defn- props? [x]
  (or (prop-map? x)
      (prop-effect? x)))

;; ----------------------------------------------

;;(defn component-result [& sub-results]
;;  (apply merge-with into sub-results))

;; ----------------------------------------------

(defn- ensure-coll [x]
  (cond-> x (not (coll? x)) vector))

(defn- parse-element-tag [s]
  (reduce (fn [acc part]
            (case (subs part 0 1)
              "." (update acc :classes conj (subs part 1))
              "#" (assoc acc :id (subs part 1))
              (assoc acc :tag-name part)))
          {:tag-name "div"
           :id nil
           :classes []}
          (re-seq #"[#.]?[^#.]+" s)))

(defn- style->str [x]
  (cond
    (map? x) (->> x
                  (map (fn [[k v]] (str (name k) ": " v)))
                  (str/join "; ")
                  (not-empty))
    :else x))

(defn- class->str [x]
  (when (some? x)
    (->> x
         (ensure-coll)
         (flatten)
         (remove nil?)
         (map name)
         (str/join " ")
         (not-empty))))

(defn- compose-prop-maps [base new]
  (let [style (into (or (:style base) {}) (:style new))
        class (into (or (:class base) []) (some-> (:class new) ensure-coll))]
    (-> base
        (into (dissoc new :style :class))
        (cond-> (seq style) (assoc :style style))
        (cond-> (seq class) (assoc :class class)))))

;; ----------------------------------------------

(defn- deref+ [x]
  (cond
    (instance? signaali.reactive.ReactiveNode x) @x
    (fn? x) (x)
    :else x))

#?(:cljs
   (defn- refs-effect [^js/Element element refs]
     (sr/create-effect
       (fn []
         (doseq [ref refs]
           (reset! ref element))
         (sr/on-clean-up (fn []
                           (doseq [ref refs]
                             (reset! ref nil))))))))

#?(:cljs
   (defn- set-element-prop [xmlns-kw ^js/Element element prop-kw prop-value]
     (let [prop-ns (namespace prop-kw)
           prop-name (name prop-kw)]
       (cond
         (= prop-ns "a")
         (-> element (.setAttribute prop-name prop-value))

         (= prop-ns "p")
         (-> element (gobj/set prop-name prop-value))

         (= prop-ns "on")
         (-> element (.addEventListener prop-name prop-value))

         ;; TODO: see if we could use `classList` on the element
         (= prop-kw :class)
         (if (= xmlns-kw :none)
           (-> element (gobj/set "className" (class->str prop-value)))
           (-> element (.setAttribute "class" (class->str prop-value))))

         (= prop-kw :style)
         (if (= xmlns-kw :none)
           (-> element (gobj/set "style" (style->str prop-value)))
           (-> element (.setAttribute "style" (style->str prop-value))))

         (= prop-kw :ref)
         nil ;; no-op

         (str/starts-with? prop-name "data-")
         (-> element (.setAttribute prop-name prop-value))

         :else
         (let [prop-value (when-not (false? prop-value) prop-value)]
           (if (= xmlns-kw :none)
             (-> element (gobj/set prop-name prop-value))
             (-> element (.setAttribute prop-name prop-value))))))))

#?(:cljs
   (defn- unset-element-prop [xmlns-kw ^js/Element element prop-kw prop-value]
     (let [prop-ns (namespace prop-kw)
           prop-name (name prop-kw)]
       (cond
         (= prop-ns "a")
         (-> element (.removeAttribute prop-name))

         (= prop-ns "p")
         (-> element (gobj/set prop-name nil))

         (= prop-ns "on")
         (-> element (.removeEventListener prop-name prop-value))

         (= prop-kw :class)
         (if (= xmlns-kw :none)
           (-> element (gobj/set "className" nil))
           (-> element (.removeAttribute "className")))

         (= prop-kw :style)
         (if (= xmlns-kw :none)
           (-> element (gobj/set "style" nil))
           (-> element (.removeAttribute "style")))

         (= prop-kw :ref)
         nil ;; no-op

         (str/starts-with? prop-name "data-")
         (-> element (.removeAttribute prop-name))

         :else
         (if (= xmlns-kw :none)
           (-> element (gobj/set prop-name nil))
           (-> element (.removeAttribute prop-name)))))))

#?(:cljs
   (defn- dynamic-props-effect [xmlns-kw ^js/Element element attrs]
     (let [attrs (->> attrs
                      ;; Combine the consecutive prop-maps together, and
                      ;; unwrap the reactive+-props in PropEffect values.
                      (into []
                            (comp
                              (partition-by prop-effect?)
                              (mapcat (fn [prop-group]
                                        (if (prop-map? (first prop-group))
                                          [(reduce compose-prop-maps {} prop-group)]
                                          (mapv :reactive+-props prop-group)))))))
           old-props (atom nil)]
       (sr/create-effect (fn []
                           (let [props (transduce (map deref+) compose-prop-maps {} attrs)]
                             (doseq [[prop-kw prop-value] @old-props
                                     :when (not (contains? props prop-kw))]
                               (unset-element-prop xmlns-kw element prop-kw prop-value))

                             (doseq [[prop-kw prop-value] props]
                               (set-element-prop xmlns-kw element prop-kw prop-value))

                             (reset! old-props props)))))))

#?(:cljs
   (defn dynamic-children-effect
     "Dynamically update the DOM node so that its children keep representing the elements array.
      The elements are either js/Element or a reactive node whose value is a sequence of js/Element."
     [^js/Element parent-element elements]
     ;; TODO: This algorithm could be improved to only replace children where it is needed.
     ;;       Would it be faster? less CPU-intensive?
     ;;       maybe different algorithms depending on the size?
     ;;       measurements needed.
     (let [xmlns-kw *xmlns-kw*
           userland-context *userland-context*]
       (sr/create-effect (fn []
                           (binding [*xmlns-kw* xmlns-kw
                                     *userland-context* userland-context]
                             (let [new-children (make-array 0)]
                               (doseq [element elements]
                                 (if (reactive-fragment? element)
                                   (doseq [sub-element @(:reactive-node element)]
                                     (.push new-children sub-element))
                                   (.push new-children element)))
                               (-> parent-element .-replaceChildren (.apply parent-element new-children)))))))))

;; ----------------------------------------------

#?(:cljs
   (defn html-text-to-dom [html-text]
     (let [^js/Element element (js/document.createElement "div")]
       (set! (.-innerHTML element) html-text)
       (.-firstElementChild element))))

(def ^:private inline-seq-children-xf
  (mapcat (fn [child] ;; Inline when child is a seq.
            (if (seq? child)
              child
              [child]))))

(defn $ [node-type & children]
  (VcupNode. node-type children))

;; ----------------------------------------------

#?(:cljs
   (defn process-vcup [vcup]
     (let [all-effects (atom [])
           to-dom-elements (fn to-dom-elements [vcup]
                             (cond
                               (nil? vcup)
                               []

                               (dom-node? vcup)
                               [vcup]

                               ;; Reactive fragment (i.e. if-fragment and for-fragment)
                               (reactive-fragment? vcup)
                               [vcup]

                               (props? vcup)
                               (throw (js/Error. "Props cannot be at the root of a scope."))

                               ;; Component result (when the component is directly invoked)
                               (component-result? vcup)
                               (let [{:keys [effects elements]} vcup]
                                 (swap! all-effects into effects)
                                 elements)

                               ;; Component invocation
                               (component-invocation? vcup)
                               (let [{component-fn :node-type
                                      args         :children} vcup]
                                 (recur (apply component-fn args)))

                               ;; Vcup fragment
                               (vcup-fragment? vcup)
                               (into []
                                     (comp inline-seq-children-xf
                                           (mapcat to-dom-elements))
                                     (:children vcup))

                               ;; ($ :div ,,,)
                               (vcup-element? vcup)
                               (let [node-type (:node-type vcup)
                                     [xmlns-kw children-xmlns-kw ^js/Element element id classes] (if (instance? js/Element node-type)
                                                                                                   ;; DOM element
                                                                                                   (let [tag-name (.-tagName node-type)
                                                                                                         [xmlns-kw children-xmlns-kw] (case (str/lower-case tag-name)
                                                                                                                                        "svg" [:svg :svg]
                                                                                                                                        "math" [:math :math]
                                                                                                                                        "foreignobject" [*xmlns-kw* :none]
                                                                                                                                        [*xmlns-kw* *xmlns-kw*])]
                                                                                                     [xmlns-kw children-xmlns-kw node-type nil nil])
                                                                                                   ;; keywords like :div and :div#id.class1.class2
                                                                                                   (let [{:keys [tag-name id classes]} (parse-element-tag (name node-type))
                                                                                                         [xmlns-kw children-xmlns-kw] (case tag-name
                                                                                                                                        "svg" [:svg :svg]
                                                                                                                                        "math" [:math :math]
                                                                                                                                        "foreignObject" [*xmlns-kw* :none]
                                                                                                                                        [*xmlns-kw* *xmlns-kw*])
                                                                                                         element (case xmlns-kw
                                                                                                                   :svg  (js/document.createElementNS xmlns-svg tag-name)
                                                                                                                   :math (js/document.createElementNS xmlns-math-ml tag-name)
                                                                                                                   :none (js/document.createElement tag-name))]
                                                                                                     [xmlns-kw children-xmlns-kw element id classes]))

                                     children (:children vcup)

                                     ;; Collect all the props.
                                     props (cons (cond-> {}
                                                         (some? id) (assoc :id id)
                                                         (seq classes) (assoc :class classes))
                                                 (filterv props? children))

                                     ;; Convert the children into elements.
                                     child-elements (binding [*xmlns-kw* children-xmlns-kw]
                                                      (into []
                                                            (comp (remove props?)
                                                                  inline-seq-children-xf
                                                                  (mapcat to-dom-elements))
                                                            children))]
                                 ;; TODO: Can we use on-dispose instead?
                                 ;; Create an effect bound to the element's lifespan.
                                 ;; It is limited to statically declared :ref props.
                                 (let [refs (into []
                                                  (comp (filter prop-map?)
                                                        (keep :ref))
                                                  props)]
                                   (when (seq refs)
                                     (swap! all-effects conj (refs-effect element refs))))

                                 ;; Set the element's props
                                 (if (every? prop-map? props)
                                   (let [composed-prop-maps (reduce compose-prop-maps {} props)]
                                     (doseq [[prop-kw prop-value] composed-prop-maps]
                                       (set-element-prop xmlns-kw element prop-kw prop-value)))
                                   (swap! all-effects conj (dynamic-props-effect xmlns-kw element props)))

                                 ;; Set the element's children
                                 (if (every? dom-node? child-elements)
                                   (doseq [child-element child-elements]
                                     (-> element (.appendChild child-element)))
                                   (swap! all-effects conj (dynamic-children-effect element child-elements)))

                                 ;; Result
                                 [element])

                               (reactive-node? vcup)
                               (let [^js/Text text-node (js/document.createTextNode "")
                                     effect (sr/create-effect (fn []
                                                                (set! (.-nodeValue text-node) @vcup)))]
                                 (swap! all-effects conj effect)
                                 [text-node])

                               :else
                               [(js/document.createTextNode vcup)]))

           elements (to-dom-elements vcup)]
       (ComponentResult. @all-effects elements))))

;; ----------------------------------------------

(defn use-effects [effects]
  (ComponentResult. effects nil))

(defn props-effect [reactive+-props]
  (PropEffect. reactive+-props))

;; ----------------------------------------------

;; Q: Why was it an effect in the first place?
;; A: It is an effect because it owns effects, not computations.
;;    The effect of the scope-effect is to trigger those effects when it is run,
;;    which makes it effectful, so it needs to be an effect too.
(defn scope-effect
  "This effects manages a static collection of effect's lifecycle so that they are
   first-run and disposed when this effect is run and cleaned up."
  ([owned-effects]
   (scope-effect owned-effects nil))
  ([owned-effects options]
   (when-some [owned-effects (seq (remove nil? owned-effects))]
     (let [scope (sr/create-effect (fn []
                                     (run! sr/run-if-needed owned-effects)
                                     (sr/on-clean-up (fn []
                                                       (run! sr/dispose owned-effects))))
                                   options)]
       (doseq [owned-effect owned-effects]
         (sr/run-after owned-effect scope))
       scope))))

(defn reactive-fragment
  ([vcup-fn]
   (reactive-fragment vcup-fn {:metadata {:name "reactive-fragment"}}))
  ([vcup-fn options]
   #?(:cljs
      (ReactiveFragment.
        (sr/create-derived (fn []
                             (let [{:keys [effects elements]} (process-vcup (vcup-fn))]
                               ;; scope-effect is inside the ReactiveFragment's effect because
                               ;; we want its lifespan to be the period between 2 re-runs of this reactive node.
                               (some-> (scope-effect effects {:dispose-on-zero-signal-watchers true})
                                       deref)
                               elements))
                           options)))))

(defmacro when-fragment [reactive+-condition then-vcup-expr]
  #?(:clj
     `(let [reactive+-condition# ~reactive+-condition
            boolean-condition# (sr/create-memo (fn []
                                                 (boolean (deref+ reactive+-condition#))))
            vcup-fn# (fn []
                       (when @boolean-condition#
                         ~then-vcup-expr))]
        (reactive-fragment vcup-fn# {:metadata {:name "when-fragment"}}))))

(defmacro if-fragment [reactive+-condition then-vcup-expr else-vcup-expr]
  #?(:clj
     `(let [reactive+-condition# ~reactive+-condition
            boolean-condition# (sr/create-memo (fn []
                                                 (boolean (deref+ reactive+-condition#))))
            vcup-fn# (fn []
                       (if @boolean-condition#
                         ~then-vcup-expr
                         ~else-vcup-expr))]
        (reactive-fragment vcup-fn# {:metadata {:name "if-fragment"}}))))

(defn- indexed-fragment [reactive-matched-index-or-nil
                         clause-index->clause-vcup
                         default-clause]
  #?(:cljs
     (reactive-fragment (fn []
                          (let [matched-index @reactive-matched-index-or-nil]
                            (cond
                              (some? matched-index)
                              (-> matched-index clause-index->clause-vcup)

                              (= default-clause ::undefined)
                              (throw (js/Error. "Missing default clause in indexed-fragment."))

                              :else
                              default-clause)))
                        {:metadata {:name "index-fragment"}})))

(defn case-fragment* [reactive+-value-expr
                      clause-value->clause-index
                      clause-index->clause-vcup
                      default-clause]
  (let [reactive-matched-index-or-nil (sr/create-memo (fn [] (-> (deref+ reactive+-value-expr)
                                                                 clause-value->clause-index)))]
    (indexed-fragment reactive-matched-index-or-nil
                      clause-index->clause-vcup
                      default-clause)))

(defmacro case-fragment [reactive+-value-expr & clauses]
  #?(:clj
     (let [[even-number-of-exprs default-clause] (if (even? (count clauses))
                                                   [clauses ::undefined]
                                                   [(butlast clauses) (last clauses)])
           clauses (partition 2 even-number-of-exprs)
           clause-value->clause-index (into {}
                                            (comp (map-indexed (fn [index [clause-value _clause-vcup]]
                                                                 (if (seq? clause-value)
                                                                   (->> clause-value
                                                                        (mapv (fn [clause-value-item]
                                                                                [clause-value-item index])))
                                                                   [[clause-value index]])))
                                                  cat)
                                            clauses)
           clause-index->clause-vcup (mapv second clauses)]
       `(case-fragment* ~reactive+-value-expr
                        ~clause-value->clause-index
                        ~clause-index->clause-vcup
                        ~default-clause))))

(defn cond-fragment* [reactive-index-fn
                      clause-index->clause-vcup]
  (indexed-fragment (sr/create-memo reactive-index-fn)
                    clause-index->clause-vcup
                    nil))

(defmacro cond-fragment [& clauses]
  (assert (even? (count clauses)) "cond-fragment requires an even number of forms")
  #?(:clj
     (let [clauses (partition 2 clauses)
           clause-index->clause-vcup (mapv second clauses)]
       `(cond-fragment* (fn []
                          (cond
                            ~@(into []
                                    (comp (map-indexed (fn [index [clause-condition _clause-vcup]]
                                                         [`~clause-condition index]))
                                          cat)
                                    clauses)))
                        ~clause-index->clause-vcup))))

#?(:cljs
   (defn for-fragment*
     ([reactive+-coll item-component]
      (for-fragment* reactive+-coll identity item-component))
     ([reactive+-coll key-fn item-component]
      (let [item-cache-atom (atom {})] ;; item-key -> [scope-effect elements]
        (ReactiveFragment.
          (sr/create-derived (fn []
                               (let [coll (deref+ reactive+-coll)

                                     ;; Update the cache:
                                     ;; - only keep the current item keys,
                                     ;; - compute values for things not already cached.
                                     old-item-cache @item-cache-atom
                                     new-item-cache (into {}
                                                          (map (fn [item]
                                                                 (let [item-key (key-fn item)]
                                                                   [item-key
                                                                    (if (contains? old-item-cache item-key)
                                                                      (get old-item-cache item-key)
                                                                      (let [{:keys [effects elements]} (process-vcup ($ item-component item))]
                                                                        [(scope-effect effects {:dispose-on-zero-signal-watchers true})
                                                                         elements]))])))
                                                          coll)]
                                 (reset! item-cache-atom new-item-cache)

                                 ;; Return the aggregated elements
                                 (into []
                                       (mapcat (fn [item]
                                                 (let [[scope elements] (get new-item-cache (key-fn item))]
                                                   ;; Makes this signal depend on the scope.
                                                   (some-> scope
                                                           deref)

                                                   ;; Return the elements
                                                   elements)))
                                       coll)))
                             {:metadata {:name "for-fragment"}}))))))

;; ----------------------------------------------

#?(:cljs
   (defn- re-run-stale-effectful-nodes-at-next-frame []
     (js/requestAnimationFrame (fn []
                                 (sr/re-run-stale-effectful-nodes)
                                 (re-run-stale-effectful-nodes-at-next-frame)))))

#?(:cljs
   (defn render [^js/Element parent-element vcup]
     ;; Remove all the children
     (.replaceChildren parent-element)

     (let [{:keys [effects]} (process-vcup ($ parent-element vcup))]
       ;; Run all the effects once, without using deref.
       (run! sr/run-if-needed effects)

       ;; Automatically refresh the DOM by re-running the effects which need a re-run.
       (re-run-stale-effectful-nodes-at-next-frame))))

#?(:cljs
   (defn dispose-render-effects []
     ;; TODO: dispose all the effects used for the rendering and the DOM updates.
     ;; In this article, we will skip this step.
     ,))
