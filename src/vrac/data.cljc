(ns vrac.data
  #?(:clj (:import [clojure.lang MapEntry
                                 ILookup
                                 IFn
                                 Seqable
                                 IDeref
                                 IAtom
                                 ISeq]))
  (:require [signaali.reactive :as sr]))

;; -----------------------
;; ReactiveNode supplement
;; -----------------------

;; Can be used to speed up "nav-in" type of functions.
(def ^:private lookup-sentinel
  #?(:cljs (js-obj)
     :clj (Object.)))

(defn data-nav
  ([reactive-data k]
   (-> @reactive-data (get k)))
  ([reactive-data k not-found]
   (-> @reactive-data (get k not-found))))

(defn data-nav-in
  ([reactive-data path]
   (data-nav-in reactive-data path nil))
  ([reactive-data path not-found]
   (loop [reactive-data reactive-data
          path (seq path)]
     (if (nil? path)
       reactive-data
       (let [next-reactive-data (data-nav reactive-data (first path) lookup-sentinel)]
         (if (identical? next-reactive-data lookup-sentinel)
           not-found
           (recur next-reactive-data (next path))))))))

;; -----------
;; ReactiveRef
;; -----------

(defn- ref-nav
  ([reactive-ref k]
   (sr/create-derived (fn [] (-> @reactive-ref (get k)))))
  ([reactive-ref k not-found]
   (sr/create-derived (fn [] (-> @reactive-ref (get k not-found))))))

;; Light wrapper which provides standard Clojure navigation.
;; Ideally it should be a subtype of ReactiveNode instead of a wrapper,
;; but it's good enough for the first version.
(deftype ReactiveRef [reactive-ref]
  #?@(:clj
      [ILookup
       (valAt [_ k] (ReactiveRef. (ref-nav reactive-ref k)))
       (valAt [_ k not-found] (ReactiveRef. (ref-nav reactive-ref k not-found)))

       IFn
       (invoke [_ k] (ReactiveRef. (ref-nav reactive-ref k)))
       (invoke [_ k not-found] (ReactiveRef. (ref-nav reactive-ref k not-found)))

       IDeref
       (deref [_] @reactive-ref)]

      :cljs
      [ILookup
       (-lookup [_ k] (ReactiveRef. (ref-nav reactive-ref k)))
       (-lookup [_ k not-found] (ReactiveRef. (ref-nav reactive-ref k not-found)))

       IFn
       (-invoke [_ k] (ReactiveRef. (ref-nav reactive-ref k)))
       (-invoke [_ k not-found] (ReactiveRef. (ref-nav reactive-ref k not-found)))

       IDeref
       (-deref [_] @reactive-ref)]))

(defn reactive-ref [target]
  (let [reactive-ref (sr/create-signal target)]
    (ReactiveRef. reactive-ref)))

(defn is-reactive-ref? [x]
  (instance? ReactiveRef x))

;; ------------------------------
;; IViewData & IViewDataProvider
;; ------------------------------

(defprotocol IViewData

  ;; Navigate
  ;; - ILookup ;; redirected to -nav
  ;; - IFn     ;; redirected to -nav
  ;; - ISeq    ;; uses -nav on each element

  ;; Read
  ;; - IDeref

  ;; Write
  ;; - IReset
  ;; - ISwap

  (-get-access-parent [this])
  (-navigate [this k not-found])

  ;; For debugging
  (-get-reactive-data [this])        ;; the reactive-data
  (-get-access-path [this]))         ;; the access path, starting from global data container.

;;(defprotocol IViewDataProvider
;;  (-get-root-view-data [this]))

;; ---------------------
;; Global data container
;; ---------------------

(defprotocol IRootViewData
  (-mount-view-data! [this k view-data-factory])
  (-unmount-view-data! [this k]))

(deftype RootViewData [mounted-view-data]
  #?@(:cljs
      [ILookup
       (-lookup [this k] (-navigate this k nil))
       (-lookup [this k not-found] (-navigate this k not-found))

       IFn
       (-invoke [this k] (-navigate this k nil))
       (-invoke [this k not-found] (-navigate this k not-found))

       IDeref
       (-deref [_] (-deref mounted-view-data))]

      :clj
      [ILookup
       (valAt [this k] (-navigate this k nil))
       (valAt [this k not-found] (-navigate this k not-found))

       IFn
       (invoke [this k] (-navigate this k nil))
       (invoke [this k not-found] (-navigate this k not-found))

       IDeref
       (deref [_] (.deref mounted-view-data))])

  IViewData
  (-get-access-parent [_] nil)
  (-navigate [_ k not-found] (-> @mounted-view-data (get k not-found)))

  ;; For debugging
  (-get-reactive-data [_] mounted-view-data)
  (-get-access-path [_] [])

  IRootViewData
  (-mount-view-data! [this k view-data-factory]
    (swap! mounted-view-data assoc k (view-data-factory this [k])))
  (-unmount-view-data! [_ k]
    (swap! mounted-view-data dissoc k)))

(defn make-root-view-data []
  (RootViewData. (sr/create-signal {})))

;; --------------
;; Re-frame store
;; --------------

(defn- get-view-data-at-path [cache-path->view-data path]
  (if (contains? @cache-path->view-data path)
    (-> @cache-path->view-data (get path))
    (-> (get-view-data-at-path cache-path->view-data (pop path))
        (get (peek path)))))

(deftype ReframeStoreViewData [cache-path->view-data
                               local-access-path
                               access-parent
                               global-access-path
                               reactive-data]
  #?@(:cljs
      [IEquiv
       (^boolean -equiv [this other]
         (if (satisfies? IViewData other)
           ;;(identical? reactive-data (-get-reactive-data ^IViewData other))
           (= global-access-path (-get-access-path ^IViewData other))
           false))

       IHash
       ;;(-hash [this] (-hash reactive-data))
       (-hash [this] (-hash global-access-path))

       ILookup
       (-lookup [this k] (-navigate this k nil))
       (-lookup [this k not-found] (-navigate this k not-found))

       IFn
       (-invoke [this k] (-navigate this k nil))
       (-invoke [this k not-found] (-navigate this k not-found))

       ISeqable
       (^clj-or-nil -seq [this]
         (let [data @reactive-data]
           (cond
             (map? data)
             (for [k (keys data)]
               (MapEntry. k (-navigate this k nil) nil))

             ;; (vector? data)
             :else
             (for [index (range (count data))]
               (-navigate this index nil)))))

       IDeref
       (-deref [_] (-deref reactive-data))

       ISwap
       (-swap! [_ f] (-swap! reactive-data f))
       (-swap! [_ f a] (-swap! reactive-data f a))
       (-swap! [_ f a b] (-swap! reactive-data f a b))
       (-swap! [_ f a b xs] (-swap! reactive-data f a b xs))

       IReset
       (-reset! [_ new-value] (-reset! reactive-data new-value))]

      :clj
      [Object
       ;; See https://clojure.org/guides/equality
       ;; We are comparing mutable objects, it should be done using `identical?`
       ;; TODO: more digging may be required, w.r.t. "Clojure Java methods equiv and hasheq"
       ;;       https://clojure.org/guides/equality#_defining_equality_for_your_own_types
       ;;       https://github.com/clojure/data.priority-map/blob/a8ea75e2196dfdc41666705c388fce22c16969fc/src/main/clojure/clojure/data/priority_map.clj#L323-L325
       (equals [this other]
         (if (satisfies? IViewData other)
           ;;(identical? reactive-data (-get-reactive-data ^IViewData other))
           (= access-path (-get-access-path ^IViewData other))
           false))

       ;; TODO: test it
       ;;(hashCode [this] (.hashCode reactive-data))
       (hashCode [this] (.hashCode access-path))

       ILookup
       (valAt [this k] (-navigate this k nil))
       (valAt [this k not-found] (-navigate this k not-found))

       IFn
       (invoke [this k] (-navigate this k nil))
       (invoke [this k not-found] (-navigate this k not-found))

       Seqable
       (seq [this]
         (let [data @reactive-data]
           (cond
             (map? data)
             (for [k (keys data)]
               (MapEntry. k (-navigate this k nil)))

             ;; (vector? data)
             :else
             (for [index (range (count data))]
               (-navigate this index nil)))))

       IDeref
       (deref [_] (.deref reactive-data))

       IAtom
       (swap [_ ^IFn f] (.swap! reactive-data f))
       (swap [_ ^IFn f ^Object a] (.swap! reactive-data f a))
       (swap [_ ^IFn f ^Object a ^Object b] (.swap! reactive-data f a b))
       (swap [_ ^IFn f ^Object a ^Object b ^ISeq xs] (.swap reactive-data f a b xs))
       (^boolean compareAndSet [_ ^Object old-value, ^Object new-value] (.compareAndSet reactive-data old-value new-value))

       (reset [_ ^Object new-value] (.reset reactive-data new-value))])

  IViewData
  (-get-access-parent [_] access-parent)
  (-navigate [this k not-found]
    (let [local-access-path (conj local-access-path k)]
      (when-not (contains? @cache-path->view-data local-access-path)
        (swap! cache-path->view-data assoc local-access-path
               (ReframeStoreViewData. cache-path->view-data
                                           local-access-path
                                           this
                                           (conj global-access-path k)
                                           (sr/create-memo (fn []
                                                             (let [child-data (-> @reactive-data (get k not-found))]
                                                               (if (-> child-data meta :ref)
                                                                 (let [^IViewData view-data (sr/with-observer nil
                                                                                               (fn []
                                                                                                 (get-view-data-at-path cache-path->view-data child-data)))]
                                                                   @view-data)
                                                                 child-data)))))))
      (-> @cache-path->view-data (get local-access-path))))

  (-get-reactive-data [_] reactive-data)
  (-get-access-path [_] global-access-path)

  ;; TODO: implement a printer, so that it's not printed as an atom

  ,)

(defn reframe-store-view-data-factory [initial-data]
  (let [cache-path->view-data (atom {})]
    (fn [access-parent global-access-path]
      (let [local-access-path []]
        (when-not (contains? @cache-path->view-data local-access-path)
          (swap! cache-path->view-data assoc local-access-path
                 (ReframeStoreViewData. cache-path->view-data
                                             local-access-path
                                             access-parent
                                             global-access-path
                                             (sr/create-state initial-data))))
        (-> @cache-path->view-data (get local-access-path))))))

;; ------------------
;; Fine-grained store
;; ------------------

;; TODO: Need a ViewData for data type (hashmap, vector, other values)
;;       with difference on which protocols are used for each type.
;; TODO: The cache in the for-fragment needs = and hashing consistency:
;;       Solution 1: return the same wrapper all the time (cache the wrappers)
;;       Solution 2: redirect = and hashing to wrapped value.
(deftype FineGrainedStoreViewData [access-parent
                                   access-path
                                   reactive-data]
  #?@(:cljs
      [IEquiv
       (^boolean -equiv [this other]
         (if (satisfies? IViewData other)
           (identical? reactive-data (-get-reactive-data ^IViewData other))
           false))

       IHash
       (-hash [this] (-hash reactive-data))

       ILookup
       (-lookup [this k] (-navigate this k nil))
       (-lookup [this k not-found] (-navigate this k not-found))

       IFn
       (-invoke [this k] (-navigate this k nil))
       (-invoke [this k not-found] (-navigate this k not-found))

       ISeqable
       (^clj-or-nil -seq [this]
         (let [data @reactive-data]
           (cond
             (map? data)
             (for [k (keys data)]
               (MapEntry. k (-navigate this k nil) nil))

             ;; (vector? data)
             :else
             (for [index (range (count data))]
               (-navigate this index nil)))))

       IDeref
       (-deref [_] (-deref reactive-data))

       ISwap
       (-swap! [_ f] (-swap! reactive-data f))
       (-swap! [_ f a] (-swap! reactive-data f a))
       (-swap! [_ f a b] (-swap! reactive-data f a b))
       (-swap! [_ f a b xs] (-swap! reactive-data f a b xs))

       IReset
       (-reset! [_ new-value] (-reset! reactive-data new-value))]

      :clj
      [Object
       ;; See https://clojure.org/guides/equality
       ;; We are comparing mutable objects, it should be done using `identical?`
       ;; TODO: more digging may be required, w.r.t. "Clojure Java methods equiv and hasheq"
       ;;       https://clojure.org/guides/equality#_defining_equality_for_your_own_types
       ;;       https://github.com/clojure/data.priority-map/blob/a8ea75e2196dfdc41666705c388fce22c16969fc/src/main/clojure/clojure/data/priority_map.clj#L323-L325
       (equals [this other]
         (if (satisfies? IViewData other)
           (identical? reactive-data (-get-reactive-data ^IViewData other))
           false))

       ;; TODO: test it
       (hashCode [this] (.hashCode reactive-data))

       ILookup
       (valAt [this k] (-navigate this k nil))
       (valAt [this k not-found] (-navigate this k not-found))

       IFn
       (invoke [this k] (-navigate this k nil))
       (invoke [this k not-found] (-navigate this k not-found))

       Seqable
       (seq [this]
         (let [data @reactive-data]
           (cond
             (map? data)
             (for [k (keys data)]
               (MapEntry. k (-navigate this k nil)))

             ;; (vector? data)
             :else
             (for [index (range (count data))]
               (-navigate this index nil)))))

       IDeref
       (deref [_] (.deref reactive-data))

       IAtom
       (swap [_ ^IFn f] (.swap! reactive-data f))
       (swap [_ ^IFn f ^Object a] (.swap! reactive-data f a))
       (swap [_ ^IFn f ^Object a ^Object b] (.swap! reactive-data f a b))
       (swap [_ ^IFn f ^Object a ^Object b ^ISeq xs] (.swap reactive-data f a b xs))
       (^boolean compareAndSet [_ ^Object old-value, ^Object new-value] (.compareAndSet reactive-data old-value new-value))

       (reset [_ ^Object new-value] (.reset reactive-data new-value))])

  IViewData
  (-get-access-parent [_] access-parent)
  (-navigate [this k not-found]
    (FineGrainedStoreViewData. this
                               (conj access-path k)
                               (-> @reactive-data (get k not-found))))

  (-get-reactive-data [_] reactive-data)
  (-get-access-path [_] access-path)

  ;; TODO: implement a printer, so that it's not printed as an atom

  ,)

(defn signalify [data]
  (let [walk-data (fn walk [data]
                    (sr/create-signal (cond
                                        ;; Do not walk in the ref's path.
                                        (-> data meta :ref)
                                        data

                                        (map? data)
                                        (update-vals data walk)

                                        (vector? data)
                                        (mapv walk data)

                                        (sequential? data) ;; lists & sequences
                                        (seq (mapv walk data))

                                        ;; Elements in a set are not "accessible" from outside, as their value needs to be in the access path.
                                        ;; They should not be transformed by this function.
                                        #_#_
                                        (set? data)
                                        (into #{} (map walk) data)

                                        :else
                                        data)))
        root-reactive-data (walk-data data)
        replace-refs! (fn replace-ref! [reactive-data]
                        (let [data @reactive-data]
                          (if (-> data meta :ref)
                            ;; Returns the signal pointed by the ref.
                            (-> root-reactive-data (data-nav-in data))

                            ;; Otherwise, replace the refs in the children and return the same signal.
                            (doto reactive-data
                              (reset! (cond
                                        (map? data)
                                        (update-vals data replace-ref!)

                                        (vector? data)
                                        (mapv replace-ref! data)

                                        (sequential? data) ;; lists & sequences
                                        (seq (mapv replace-ref! data))

                                        :else
                                        data))))))]
    ;; Replaces signals pointing to refs with the signals pointed by the refs.
    (replace-refs! root-reactive-data)))

;; TODO: Make it robust against infinite loops.
(defn underefy [x]
  (cond
    #? (:cljs (satisfies? IDeref x)
        :clj (instance? IDeref x))
    (recur (deref x))

    (map? x)
    (update-vals x underefy)

    (vector? x)
    (mapv underefy x)

    (sequential? x) ;; lists & sequences
    (map underefy x)

    :else
    x))

(defn fine-grained-store-view-data-factory [initial-data]
  (fn [access-parent access-path]
    (FineGrainedStoreViewData. access-parent
                               access-path
                               (signalify initial-data))))
