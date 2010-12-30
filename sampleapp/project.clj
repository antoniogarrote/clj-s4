(defproject sampleapp "1.0.0-SNAPSHOT"
  :description "a demo S4 app built using clj-s4"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-s4/clj-s4 "0.2.1"]]
  :s4-app {:name "RandomNumbers"
           :namespace "sample.app.core"
           :configuration ["sampleapp.wiring" "RandomNumbers"]
           :adapters ["sampleapp.wiring" "RandomNumbersAdapter"]})
