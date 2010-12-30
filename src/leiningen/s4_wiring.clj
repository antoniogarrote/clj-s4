(ns leiningen.s4-wiring
  "Outputs the wiring XML for a S4 component"
  (:use clj-s4.wiring))

(defn s4-wiring
  "Must receive two arguments
     -namespace: containing the wiring for the components
     -component-id: identifier of the component wiring to generate"
  ([namespace component-id]
       (use (symbol namespace))
       (println (gen-xml component-id))))
