(defproject randomnumbers "1.0.0-SNAPSHOT"
  :description "a demo S4 app built using clj-s4"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-s4 "0.2.1.1-SNAPSHOT"]]
  :dev-dependencies [[clj-s4 "0.2.1-SNAPSHOT"]]
  :aot [randomnumbers.core]
  :s4-app {:name "RandomNumbers"
           :namespace "randomnumbers.core"
           :configuration ["randomnumbers.wiring" "RandomNumbers"]
           :adapters ["randomnumbers.wiring" "RandomNumbersAdapter"]})
