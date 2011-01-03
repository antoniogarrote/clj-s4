(ns wordcount.core
  (:use clj-s4.core)
  (:use opennlp.nlp))

;; Messages

(def-s4-message cljs4.Text [:content :id])
(def-s4-message cljs4.Sentence [[String :content] [String :textId]])
(def-s4-message cljs4.Word [[String :content] [String :textId] [String :pos]])

;; PEs

(def-s4-pe cljs4.SentenceSplitter [:component :model :stream]
     :init (fn [this]
             (let [model (read-state this :model)
                   component (make-sentence-detector model)]
               (write-state this :component component)))
     :process-event [[Object] (fn [this text]
                                  (let [_ (println (str "MSG: " text))
                                        text (msg-to-map text)
                                        text-id (or (:id text) (:textId text))
                                        content (:content text)
                                        sentences ((read-state this :component)  content)]
                                    (doseq [sentence sentences]
                                      (dispatch-event this
                                                      (read-state this :stream)
                                                      (cljs4.Sentence. {:content sentence :textId (:id text)})))))]
     :output (fn [this] :not-interested))

(def-s4-pe cljs4.Tokenizer [:tokenizer :posTagger :tokenizerModel :posTaggerModel :stream]
  :init (fn [this]
          (let [tokenizer-model (read-state this :tokenizerModel)
                pos-tagger-model (read-state this :posTaggerModel)
                tokenizer (make-tokenizer tokenizer-model)
                pos-tagger (make-pos-tagger pos-tagger-model)]
            (write-state this :tokenizer tokenizer)
            (write-state this :posTagger pos-tagger)))
  :process-event [[Object] (fn [this text]
                             (let [_ (println (str "MSG: " text))
                                   text (msg-to-map text)
                                   text-id (:textId text)
                                   content (:content text)
                                   tokenize (read-state this :tokenizer)
                                   pos-tag (read-state this :posTagger)
                                   products (pos-tag (tokenize  content))]
                               (doseq [[token pos] products]
                                 (dispatch-event this
                                                 (read-state this :stream)
                                                 (cljs4.Word. {:content token :pos pos :textId text-id})))))]
  :output (fn [this] :not-interested))

(def *stop-words*
     ["I"  "a"  "about"  "an"  "are"  "as"  "at"  "be"  "by"  "com"  "for"  "from"  "how"  "in"
      "is"  "it"  "of"  "on"  "or"  "that"  "the"  "this"  "to"  "was"  "what"  "when"  "where"
      "who"  "will"  "with"  "the"  "www"])

(def-s4-pe cljs4.StopWordsFilter [:stream]
  :process-event [[Object] (fn [this word]
                             (let [_ (println (str "MSG: " word))
                                   word-map (msg-to-map word)]
                               (when (nil? (some #(= % (:content word-map)) *stop-words*))
                                 (dispatch-event this
                                                 (read-state this :stream)
                                                 word))))]
  :output (fn [this] :not-interested))


(def-s4-pe cljs4.PosPersister [:filePath :acum :textId]
  :init (fn [this]
          (let [acum {}]
            (write-state this :acum acum)))

  :process-event [[Object] (fn [this token]
                             (let [token-map (msg-to-map token)]
                               (write-state this :textId (:textId token-map))
                               (alter-state this :acum
                                            (fn [old] (let [old-count (get old (:pos token-map))]
                                                       (if (nil? old-count) (assoc old (:pos token-map) 1)
                                                           (assoc old (:pos token-map) (inc old-count))))))))]
  :output (fn [this]
            (let [ac (read-state this :acum)]
              (spit (str (read-state this :filePath) "/" (read-state this :textId) ".txt")
                    (reduce str "" (map (fn [[pos count]] (str "POS: " pos " COUNT: " count " \r\n")) (read-state this :acum)))))))

;; Random text adapter

(def *texts*
     ["Now is the winter of our discontent. Made glorious summer by this sun of York"
      "All propositions are of equal value"])

(defn random-text
  ([] (let [pos (mod (int (* 100 (rand))) 2)]
        (nth *texts* pos))))

(defn gen-uuid
  ([] (.replace (str (java.util.UUID/randomUUID)) "-" "")))

(def-s4-adapter cljs4.RandomText [:stream]

  :init (fn [this args]
          (.start (Thread. this)))

  :run (fn [this]
         (loop [text (random-text)]
           (generate-event this (:stream (deref (.state this))) (cljs4.Text. {:content text :id (gen-uuid)}))
           (Thread/sleep 10000)
           (recur (random-text)))))

