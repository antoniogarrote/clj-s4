(defproject wordcount "1.0.0-SNAPSHOT"
  :description "FIXME: write"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-s4 "0.2.1.1-SNAPSHOT"]
                 [clojure-opennlp "0.1.1"]]
  :repositories {"opennlp.sf.net" "http://opennlp.sourceforge.net/maven2"}
  :dev-dependencies [[clj-s4 "0.2.1-SNAPSHOT"]
                     [clojure-opennlp "0.1.1"]
                     [leiningen/lein-swank "1.2.0-SNAPSHOT"]]
  :aot [wordcount.core]
  :s4-app {:name "WordCountAnalysis"
           :namespace "wordcount.core"
           :configuration ["wordcount.wiring" "WordCountAnalysis"]
           :adapters ["wordcount.wiring" "WordCountAnalysisAdapter"]})
