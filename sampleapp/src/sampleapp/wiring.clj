(ns sampleapp.wiring
  (:use [clj-s4.wiring]))

;;; Processing elements

(wire-app "RandomNumbers"

 (wire-pe {:id "bifurcationPE"
           :class "cljs4.BifurcationPE"
           :keys ["RandomNumbers *"]
           :properties
           [{:name "id" :value "bifurcationPe"}
            {:name "dispatcher" :ref "numbersDispatcher"}
            {:name "bifurcationPredicate" :value "(fn [n] (if (odd? n) \"OddNumbers\" \"EvenNumbers\"))"}]})

 (wire-pe {:id "OddNumsAggregatorPE"
           :class "cljs4.NumAggregatorPE"
           :keys ["OddNumbers *"]
           :properties
           [{:name "id" :value "OddNumbersAggregatorPE"}]})

 (wire-pe {:id "EvenNumsAggregatorPE"
           :class "cljs4.NumAggregatorPE"
           :keys ["EvenNumbers *"]
           :properties
           [{:name "id" :value "EvenNumbersAggregatorPE"}]})

 (wire-partitioner {:id "mapPartitioner"
                    :stream-names ["RandomNumbers" "OddNumbers" "EvenNumbers"]
                    :hash-keys ["num"]})

 (wire-dispatcher {:id "numbersDispatcher"
                   :partitioners ["mapPartitioner"]}))

;;; Adapter

(wire-adapters "RandomNumbersAdapter"

 (wire-bean
  {:id "randomNumbersGenerator"
   :class "cljs4.RandomNumberAdapter"
   :properties [{:name "stream" :value "RandomNumbers"}]}))
