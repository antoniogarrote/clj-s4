(ns clj-s4.wiring
  (:use hiccup.core)
  (:use hiccup.page-helpers))

(def *xml-register* (ref {}))

(defn- register-wiring
  ([class gen-fn] (dosync (alter *xml-register* (fn [old] (assoc old (str class) gen-fn))))))

(defn gen-xml
  "Generates wiring XML for a component"
  ([id] (html ((get (deref *xml-register*) id)))))

(defn gen-xml-app
  ([name] (str (xml-declaration "UTF-8")
               (html ((get (deref *xml-register*) name))))))

(defn- bean-output-frequency
  ([[kind freq]]
     (condp = kind
         :event-count [:property {:name "outputFrequencyByEventCount" :value freq}]
         :time-boundary [:property {:name "outputFrequencyByTimeBoundary" :value freq}]
         :offset        [:property {:name "outputFrequencyOffset" :value freq}])))


(defn wire-pe
  ([args]
     (if (reduce (fn [ac v] (and ac v)) true
                 (map (fn [k] (not (empty?
                                   (filter (fn [p] (= p k))
                                           (keys args)))))
                      [:id :class :keys]))
       (let [bean-map [:bean {:id (:id args) :class (:class args)}
                       [:property {:name "keys"}
                        [:list (map (fn [k] [:value k]) (:keys args))]]
                       (when (not (nil? (:properties args)))
                         (map (fn [p] (let [name (:name p)
                                           value (:value p)
                                           ref (:ref p)]
                                       [:property {:name name (if (nil? value) :ref :value) (if (nil? value) ref value)}]))
                              (:properties args)))
                       (if (nil? (:output-frequency args))
                         (bean-output-frequency [:event-count 1])
                         (bean-output-frequency (:output-frequency args)))
                       (if (nil? (:ttl args))
                         [:property {:name "ttl" :value "-1"}]
                         [:property {:name "ttl" :value (:ttl args)}])]]
         (do (register-wiring (:id args) (fn [] bean-map))
             (:id args)))
       (throw (Exception. (str "Missing :id, :class or :keys in pe description " args))))))

(defn wire-partitioner
  ([args]
     (if (reduce (fn [ac v] (and ac v)) true
                 (map (fn [k] (not (empty?
                                   (filter (fn [p] (= p k))
                                           (keys args)))))
                      [:id :stream-names :hash-keys]))
       (let [bean-map [:bean {:id (:id args) :class (or (:class args) "io.s4.dispatcher.partitioner.DefaultPartitioner")}
                       [:property {:name "streamNames"}
                        [:list
                         (map (fn [stream] [:value stream]) (:stream-names args))]]
                       [:property {:name "hashKey"}
                        [:list
                         (map (fn [hash-key] [:value hash-key]) (:hash-keys args))]]
                       [:property {:name "hasher" :ref (or (:hasher args) "hasher")}]
                       [:property {:name "debug" :value (or (:debug args) "false")}]]]
         (do (register-wiring (:id args) (fn [] bean-map))
             (:id args)))
       (throw (Exception. (str "Missing :id, :stream-names or :hash-keys in partitioner description"))))))

(defn wire-dispatcher
  ([args]
     (if (reduce (fn [ac v] (and ac v)) true
                 (map (fn [k] (not (empty?
                                   (filter (fn [p] (= p k))
                                           (keys args)))))
                      [:id :partitioners]))
       (let [bean-map [:bean {:id (:id args)
                              :class (or (:class args) "io.s4.dispatcher.Dispatcher")
                              :init-method (or (:class args) "init")}
                       [:property {:name "partitioners"}
                        [:list
                         (map (fn [partitioner-id] [:ref {:bean partitioner-id}]) (:partitioners args))]]
                       [:property {:name "eventEmitter" :ref (or (:event-emitter args) "commLayerEmitter")}]
                       [:property {:name "loggerName" :value (or (:logger-name args) "s4")}]]]
         (do (register-wiring (:id args) (fn [] bean-map))
             (:id args)))
       (throw (Exception. "Missing :id or :partitioners in dispatcher description")))))

(defn wire-bean
  ([args]
     (if (reduce (fn [ac v] (and ac v)) true
                 (map (fn [k] (not (empty?
                                   (filter (fn [p] (= p k))
                                           (keys args)))))
                      [:id :class]))
       (let [bean-map [:bean {:id (:id args) :class (:class args)}
                       (when (not (nil? (:properties args)))
                         (map (fn [p] (let [name (:name p)
                                           value (:value p)
                                           ref (:ref p)]
                                       [:property {:name name (if (nil? value) :ref :value) (if (nil? value) ref value)}]))
                              (:properties args)))]]
         (do (register-wiring (:id args) (fn [] bean-map)) bean-map))
       (throw (Exception. "Missing :id or :class arguments")))))

(defn wire-join-bean
  ([args]
     (if (reduce (fn [ac v] (and ac v)) true
                 (map (fn [k] (not (empty?
                                   (filter (fn [p] (= p k))
                                           (keys args)))))
                      [:id :keys :include-fields :output-stream :output-class]))
       (let [bean-map [:bean {:id (:id args) :class "io.s4.processor.JoinPE"}
                       [:property {:name "keys"}
                        [:list
                         (map (fn [key] [:value key]) (:keys args))]]
                       [:property {:name "includeFields"}
                        [:list
                         (map (fn [field] [:value field]))]]
                       [:property {:name "outputStreamName" :value (:output-stream args)}]
                       [:property {:name "outputClassName" :value (str (:output-class args))}]
                       (when (not (nil? (:properties args)))
                         (map (fn [p] (let [name (:name p)
                                           value (:value p)
                                           ref (:ref p)]
                                       [:property {:name name (if (nil? value) :ref :value) (if (nil? value) ref value)}]))
                              (:properties args)))]]
         (do (register-wiring (:id args) (fn [] bean-map)) bean-map))
       (throw (Exception. "Missing :id, :keys, :include-fields, :otput-stream or :output-class arguments")))))

(defn wire-app
  ([name & ids]
     (let [bean-map [:beans {:xmlns "http://www.springframework.org/schema/beans"
                             "xmlns:xsi" "http://www.w3.org/2001/XMLSchema-instance"
                             "xsi:schemaLocation" "http://www.springframework.org/schema/beans             http://www.springframework.org/schema/beans/spring-beans-2.0.xsd"}
                     (map (fn [id] (if (string? id) ((get (deref *xml-register*) id)) id)) ids)]]
       (do (register-wiring name (fn [] bean-map))
           name))))

(defn wire-adapters
  ([name & ids]
     (apply wire-app (concat [name] ids))))
