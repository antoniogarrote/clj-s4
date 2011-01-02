(ns wordcount.wiring
  (:use [clj-s4.wiring]))

(wire-app "WordCountAnalysis"
  (wire-pe {:id "sentenceSplitter"
            :class "cljs4.SentenceSplitter"
            :keys  ["Text *"]
            :properties
            [{:name "model" :value "/Users/antoniogarrote/Development/Projects/s4/clj-s4/sampleapps/wordcount/models/en-sent.bin"}
             {:name "dispatcher" :ref "wordsDispatcher"}
             {:name "stream" :value "Sentences"}]})

  (wire-pe {:id "tokenizer"
            :class "cljs4.Tokenizer"
            :keys  ["Sentences textId"]
            :properties
            [{:name "tokenizerModel" :value "/Users/antoniogarrote/Development/Projects/s4/clj-s4/sampleapps/wordcount/models/en-token.bin"}
             {:name "posTaggerModel" :value "/Users/antoniogarrote/Development/Projects/s4/clj-s4/sampleapps/wordcount/models/en-pos-maxent.bin"}
             {:name "dispatcher" :ref "wordsDispatcher"}
             {:name "stream" :value "Tokens"}]})

  (wire-pe {:id "stopWordsFilter"
            :class "cljs4.StopWordsFilter"
            :keys  ["Tokens textId"]
            :properties
            [{:name "dispatcher" :ref "wordsDispatcher"}
             {:name "stream" :value "FilteredTokens"}]})

  (wire-pe {:id "posPersister"
            :class "cljs4.PosPersister"
            :keys  ["FilteredTokens textId"]
            :properties
            [{:name "dispatcher" :ref "wordsDispatcher"}
             {:name "filePath" :value "/tmp/postaggers"}]})

  (wire-partitioner {:id "textPartitioner"
                     :stream-names ["Text"]
                     :hash-keys ["content"]})

  (wire-partitioner {:id "sentencesPartitioner"
                     :stream-names ["Sentences" "Tokens" "FilteredTokens"]
                     :hash-keys ["textId"]})

 (wire-dispatcher {:id "wordsDispatcher"
                   :partitioners ["sentencesPartitioner" "textPartitioner"]}))

(wire-adapters "WordCountAnalysisAdapter"
  (wire-bean
   {:id "randomTextGenerator"
    :class "cljs4.RandomText"
    :properties [{:name "stream" :value "Text"}]}))
