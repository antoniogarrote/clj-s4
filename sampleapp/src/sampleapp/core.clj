(ns sampleapp.core
  (:use clj-s4.core))

(def-s4-message cljs4.Number [[int :num]])
;
(def-s4-pe cljs4.NumAggregatorPE [:acum]

  :process-event [[Object] (fn [this event]
                             (let [event (msg-to-map event)]
                               (dosync (alter (.state this)
                                              (fn [old-state]
                                                (assoc old-state :acum
                                                       (+ (or (:acum old-state) 0) (:num event))))))))]

  :output (fn [this] (println (str "NumAgreggatorPE: Acum output: " (.getAcum this)))))

 (def-s4-pe cljs4.BifurcationPE [:stream :bifurcationPredicate]

   :process-event [[Object] (fn [this event]
                              (let [event (msg-to-map event)
                                    predicate (eval (read-string (.getBifurcationPredicate this)))
                                    stream (predicate (:num event))]
                                (dispatch-event this stream (cljs4.Number. event))))]

   :output (fn [this] :not-interested))

 (def-s4-adapter cljs4.RandomNumberAdapter [:stream]

   :init (fn [this args]
          (.start (Thread. this)))

   :run (fn [this]
          (loop [num (int (Math/floor (* (rand) 100)))]
            (generate-event this (.getStream this) (cljs4.Number. {:num num}) [(mod num 10)])
            (Thread/sleep 3000)
            (recur (int (Math/floor (* (rand) 100)))))))
