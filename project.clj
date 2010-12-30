(defproject clj-s4 "0.2.1-SNAPSHOT"
  :description "A library to build Yahoo S4 applications using Clojure"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [bcel/bcel "5.1"]
                 [hiccup "0.3.1"]
                 [clj-s4-core-deps "0.2.1-SNAPSHOT"]]
  :aot [clj-s4.core sampleapp.core]
  :dev-dependencies [[leiningen/lein-swank "1.2.0-SNAPSHOT"]
                     [cdt "1.2"]
                     [hiccup "0.3.1"]])
;  :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8030"])
