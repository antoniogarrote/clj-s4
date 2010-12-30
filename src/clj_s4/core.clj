(ns clj-s4.core
  (:require [clojure.contrib.logging :as logging]))

(defn capitalize
  ([text]
     (if (= "" text)
       text
       (let [fs (.substring text 0 1)
             l   (.length text)
             rs (.substring text 1 l)]
         (str (.toUpperCase fs) rs)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Processing Elements
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro def-pe-getters
  ([pe-id attrs]
     (let [funcs (map (fn [k] `(defn ~(symbol (str "m_" pe-id "_get" (capitalize (name k)))) [this#] (get @(.state this#) ~k))) attrs)]
       `(do ~@funcs))))

(defmacro def-pe-setters
  ([pe-id attrs]
     (let [funcs (map (fn [k] `(defn ~(symbol (str "m_" pe-id "_set" (capitalize (name k)))) [this# val#] (dosync (alter (.state this#) assoc ~k val#)))) attrs)]
       `(do ~@funcs))))

(defmacro def-pe-proces-events
  ([pe-id [process-events-types process-events-fn]]
     `(defn ~(symbol (str "m_" pe-id "_processEvent")) [this# event#]
        (~process-events-fn this# event#))))

(defmacro def-pe-output
  ([pe-id output-fn]
     (when (not (nil? output-fn))
       (do
       `(defn ~(symbol (str "m_" pe-id "_output")) [this#]
          (~output-fn this#))))))

(defmacro def-initializer
  ([pe-id]
     `(defn ~(symbol (str "m_" pe-id "_initialize")) [] [[] (ref {})])))

(defmacro methods-description
  ([attributes]
     (let [funcs-set (map (fn [property]
                            `[~(symbol (str "set" (capitalize (name property)))) ~'[Object] ~'void])
                          attributes)]
       `[~@funcs-set])))

(defn methods-desc
  ([attributes methods]
     (vec (concat (map (fn [attr] [(symbol (str "get" (capitalize (name attr)))) [] 'Object]) (filter (partial not= :id) attributes))
                  (map (fn [attr] [(symbol (str "set" (capitalize (name attr)))) (if (= attr :id) ['String] ['Object]) 'void]) attributes)
                  (if (nil? methods) [] (map (fn [type] ['processEvent [type] 'void]) (first (:process-event methods))))))))

(defmacro def-s4-pe
  ([name state & methods]
     (let [state (vec (set (concat state [:id :dispatcher])))
           pe-id (.replace (str (java.util.UUID/randomUUID)) "-" "")
           methods (apply hash-map methods)]
       `(do
          (def-initializer ~pe-id)
          (def-pe-getters ~pe-id ~state)
          (def-pe-setters ~pe-id ~state)
          (def-pe-proces-events ~pe-id ~(:process-event methods))
          (def-pe-output ~pe-id ~(:output methods))
          (gen-class
           :name ~name
           :prefix ~(str "m_" pe-id "_")
           :init ~(symbol  "initialize")
           :extends io.s4.processor.AbstractPE
           :state ~'state
           :methods ~(methods-desc state methods)
           )))))

(defn dispatch-event
  ([pe stream event]
     (let [dispatcher (.getDispatcher pe)
           _ (println (str "dispatchin to dispatcher: " dispatcher " stream " stream " event " event))]
       (.dispatchEvent dispatcher stream event))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Adapters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro def-adapter-run
  ([pe-id run-fn]
     (when (not (nil? run-fn))
       (do
       `(defn ~(symbol (str "m_" pe-id "_run")) [this#]
          (~run-fn this#))))))

(defmacro def-adapter-initializer
  ([pe-id]
     `(defn ~(symbol (str "m_" pe-id "_initialize")) [] [[] (ref {:handlers []})])))

(defmacro def-adapter-postinitializer
  ([pe-id init-fn]
     `(defn ~(symbol (str "m_" pe-id "_postinitialize")) [this# & args#] (~init-fn this# args#))))

(defmacro def-adapter-handlers-fn
  ([pe-id]
     `(do
        (defn ~(symbol (str "m_" pe-id "_addHandler")) [this# handler#]
          (dosync (alter (.state this#)
                         (fn [state#] (let [handlers# (:handlers state#)]
                                       (assoc state# :handlers (conj handlers# handler#)))))))
        (defn ~(symbol (str "m_" pe-id "_removeHandler")) [this# handler#]
          (dosync (alter (.state this#)
                         (fn [state#] (let [handlers# (:handlers state#)]
                                       (assoc state# :handlers
                                              (filter (fn [old-handler#]
                                                        (not= old-handler# handler#))
                                                      handlers#))))))))))

(defmacro def-s4-adapter
  ([name state & methods]
     (let [state (vec (set (concat state [:id :stream :handlers])))
           pe-id (.replace (str (java.util.UUID/randomUUID)) "-" "")
           methods (apply hash-map methods)
           init-method (:init methods)
           run-method (:run methods)]
       `(do
          (def-adapter-initializer ~pe-id)
          (def-adapter-postinitializer ~pe-id ~init-method)
          (def-pe-getters ~pe-id ~state)
          (def-pe-setters ~pe-id ~state)
          (def-adapter-run ~pe-id ~run-method)
          (def-adapter-handlers-fn ~pe-id)
          (gen-class
           :name ~name
           :prefix ~(str "m_" pe-id "_")
           :init ~(symbol  "initialize")
           :post-init ~(symbol "postinitialize")
           :implements [io.s4.listener.EventListener
                        java.lang.Runnable]
           :state ~'state
           :methods ~(methods-desc state methods)
           )))))

(defn generate-event
  "Sends a new event to all the handlers registered for a certain adapter.
   Arguments:
   - adapter: the adapter object
   - stream : a string containing the name of the stream where the event will be inserted
   - event:   a object containing data for the event
   - keys:    a list of keys for the event"
  ([adapter stream event keys]
     (let [handlers (:handlers (deref (.state adapter)))
           wrapper (io.s4.collector.EventWrapper. stream event keys)]
       (doseq [handler handlers]
         (try
           (.processEvent handler wrapper)
           (catch Exception ex
             (logging/error "error in event handler" ex)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Partitioner
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn partition-stream?
  ([partitioner stream]
     (let [streams (:streams (deref (.state partitioner)))]
       (empty? (filter (fn [s] (= s stream)) streams)) false true)))


(defn m_hmp_partition
  ([this stream event partition-count]
     (logging/info (str "PARTITION OF : " event))
     (if (map? event)
       (if (partition-stream? this stream)
         (let [key (:key (deref (.state this)))
               hasher (:hasher (deref (.state this)))
               value (or ((keyword key) event)
                         (get event key))]
           (if (nil? value)
             nil
             (let [partitionInfo (io.s4.dispatcher.partitioner.CompoundKeyInfo.)
                   partitionId (mod (.hash hasher value) partition-count)]
               (.setPartitionId partitionInfo partitionId)
               (.setCompoundValue partitionInfo (str value))
               (logging/info (str "EMITTING : " partitionInfo))
               [partitionInfo])))
         nil)
       nil)))

(defn m_hmp_initialize
  ([] [[] (ref {:hasher (io.s4.dispatcher.partitioner.DefaultHasher.)})]))

(def-pe-getters
  "hmp"
  [:hasher :streams :key])

(def-pe-setters
  "hmp"
  [:hasher :streams :key])


;;; Hash map partitioner
(gen-class :name clj_s4.ClojureHashMapPartitioner
           :prefix "m_hmp_"
           :implements [io.s4.dispatcher.partitioner.Partitioner]
           :state state
           :init initialize
           :methods [[getHasher [] Object]
                     [getStreams [] Object]
                     [getKey [] Object]
                     [setHasher [Object] void]
                     [setStreams [Object] void]
                     [setKey [Object] void]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro def-message-initializer
  ([pe-id]
     `(defn ~(symbol (str "m_" pe-id "_initialize"))
        ([components#] [[] (reduce (fn [ac# [k# v#]] (do (.put ac# (name k#) v#) ac#)) (java.util.HashMap.) components#)])
        ([] [[] (java.util.HashMap.)]))))

(defmacro def-message-to-map
  ([pe-id components]
     `(defn ~(symbol (str "m_" pe-id "_toMap")) [this#]
        (map (fn [c#] (str ".get" (capitalize (name c#)))) ~components))))

(defmacro def-message-getters
  ([pe-id attrs]
     (let [funcs (map (fn [k] `(defn ~(symbol (str "m_" pe-id "_get" (capitalize (name k)))) [this#] (.get (.state this#) (name ~k)))) attrs)]
       `(do ~@funcs))))

(defmacro def-message-setters
  ([pe-id attrs]
     (let [funcs (map (fn [k] `(defn ~(symbol (str "m_" pe-id "_set" (capitalize (name k)))) [this# val#] (.put (.state this#) (name ~k) val#))) attrs)]
       `(do ~@funcs))))


(defn msg-to-map
  "Transforms a message object into a Clojure map"
  ([msg]
     (reduce (fn [ac g]
               (let [gk (.toLowerCase (aget (.split g ".get") 1))
                     method (first (filter (fn [m] (= (.getName m) gk)) (.getMethods (.getClass msg))))]
                 (assoc ac (keyword gk) (.invoke method msg nil))))
             {}
             (.toMap msg))))

(defmacro def-pe-plain-getters
  ([pe-id attrs]
     (let [funcs (map (fn [k] `(defn ~(symbol (str "m_" pe-id "_" (name k))) [this#] (.get (.state this#) (name ~k)))) attrs)]
       `(do ~@funcs))))

(defn methods-message-desc
  ([attributes]
     (vec (concat

           (map (fn [attr] (if (coll? attr) (let [[type attr-name] attr]
                                             [(symbol (str "get" (capitalize (name attr-name)))) [] type])
                              [(symbol (str "get" (capitalize (name attr)))) [] 'Object]))
                (filter (partial not= :id) attributes))

           (map (fn [attr] (if (coll? attr) (let [[type attr-name] attr]
                                             [(symbol (name attr-name)) [] type])
                              [(symbol (name attr)) [] 'Object]))
                (filter (partial not= :id) attributes))

           (map (fn [attr] (if (coll? attr)
                            (let [[type attr-name] attr]
                              [(symbol (str "set" (capitalize (name attr-name)))) [type] 'void])
                            [(symbol (str "set" (capitalize (name attr)))) ['Object] 'void])) attributes)
                  [['toMap [] 'Object]]))))


(defmacro def-s4-message
  ([name components-and-types]
     (let [pe-id (.replace (str (java.util.UUID/randomUUID)) "-" "")
           components (vec (map (fn [ct] (if (coll? ct) (second ct) ct)) components-and-types))]
       `(do
          (def-message-initializer ~pe-id)
          (def-pe-plain-getters ~pe-id ~components)
          (def-message-getters ~pe-id ~components)
          (def-message-setters ~pe-id ~components)
          (def-message-to-map ~pe-id ~components)
          (gen-class
           :name ~name
           :prefix ~(str "m_" pe-id "_")
           :init ~(symbol  "initialize")
           :constructors {[Object] []
                          [] []}
           :state ~'state
           :methods ~(methods-message-desc components-and-types)
           )))))
