# clj-s4

A library for building applications using Yahoo S4 distributed stream platform in the Clojure programming language.

## Usage

A sample application project is included in the *sampleapp* directory of this project.

### Defining events

You can define a event object that can be received by processing elements using the *def-s4-message* macro.
The macro works in a similar way to the *deftype* macro, generating a new class with the specified name and fields, but it will also generate standard getter and setter methods so the message object can be easilly wired using Spring.
Messages can be built from Clojure maps and transformed back into maps using the *msg-to-map* function:

     ;; Generates a cljs4.tests.Msg class with fields 'foo' with type int
     ;; and 'bar' of type Object
     (def-s4-message cljs4.tests.Msg [[int :foo] :bar])
     (def *msg* (cljs4.tests.Msg. {:foo 1 :bar "test"}))
     (.getFoo *msg*)
     ; returns 1
     (msg-to-map *msg*)
     ; returns {:foo 1 :bar "test"}

### Defining Processing Elements

S4 processing elements (PEs) can be defined using the *def-s4-pe* macro.
The macro receives as arguments the name of the class for the PE, the fields containing the state of the PE, and implementations for the *processEvent* and *output* functions.
The state will be stored in a Clojure reference that can be accessed using the *state* java method on the *this* reference:

    (def-s4-pe cljs4.NumAggregatorPE [:acum]

      :process-event [[Object] (fn [this event]
                                 (let [event (msg-to-map event)]
                                   (dosync (alter (.state this)
                                                  (fn [old-state]
                                                    (assoc old-state :acum
                                                           (+ (or (:acum old-state) 0) (:num event))))))))]

      :output (fn [this] (println (str "NumAgreggatorPE: Acum output: " (.getAcum this)))))

S4 PEs can emit new events invoking the *dispatch-event* function from the *processEvent* method:

    ; Dispatches an event using the message class defined earlier
    (dispatch-event this stream-name (cljs4.tests.Msg. {:foo 1 :bar "test"}))

### Defining adapters

S4 dispatchers can be defined using the *def-s4-adapter* macro. This macro expects a few arguments:  the class, the state fields and a couple of function implementations. The functions to implement are the init function to *init* the state of the adapter and the *run* function with the code for the main loop of the adapter:

    (def-s4-adapter cljs4.RandomNumberAdapter [:stream]

      :init (fn [this args]
         (.start (Thread. this)))

      :run (fn [this]
      (loop [num (int (Math/floor (* (rand) 100)))]
        (generate-event this (.getStream this) (cljs4.Number. {:num num}) [(mod num 10)])
          (Thread/sleep 3000)
            (recur (int (Math/floor (* (rand) 100)))))))

Adapters can emit new events using the *generate-event* function.

### Wiring

clj-s4 includes some functions to describe the wiring of S4 PEs, adapters, etc using Lisp s-expressions instead of XML files.

PEs can be wired using the *wire-pe* function:

    (wire-pe {:id "OddNumsAggregatorPE"
       :class "cljs4.NumAggregatorPE"
       :keys ["OddNumbers *"]
       :properties
         [{:name "id" :value "OddNumbersAggregatorPE"}]})

Other java beans can be wired using the *wire-bean* function:

      (wire-bean
        {:id "randomNumbersGenerator"
         :class "cljs4.RandomNumberAdapter"
         :properties [{:name "stream" :value "RandomNumbers"}]})

Collections of beans (PEs and other components) can be collected in a S4 application wiring using the *wire-app* function:

    (wire-app "RandomNumbers"

     (wire-pe {:id "bifurcationPE"
      :class "cljs4.BifurcationPE"
      :keys ["RandomNumbers *"]
      :properties
        [{:name "id" :value "bifurcationPe"}
         {:name "dispatcher" :ref "numbersDispatcher"}]})

          ; other beans

      )

In the same way collections of beans can be collected in a S4 adapter wiring using the *wire-adapters* function:

    (wire-adapters "RandomNumbersAdapter"

      (wire-bean
      {:id "randomNumbersGenerator"
       :class "cljs4.RandomNumberAdapter"
       :properties [{:name "stream" :value "RandomNumbers"}]}))

Beans wiring XML, applications and adapters generated wiring XML can be generated using the functions *gen-xml* and *gen-xml-app* passing as an argument the ID of the bean or the name passed as the first parameter to the *wire-app* and *wire-adapters* functions.

### Deploying

clj-s4 includes a Leiningen task to bundle a S4 application.
This task uses the information for the application that must be defined in the project.clj file for the leiningen project:

     (defproject sampleapp "1.0.0-SNAPSHOT"
       :description "a demo S4 app built using clj-s4"
         :dependencies [[org.clojure/clojure "1.2.0"]
         		 [org.clojure/clojure-contrib "1.2.0"]
                         [clj-s4/clj-s4 "0.2.1"]]
       :s4-app {:name "RandomNumbers"
       :namespace "sample.app.core"
       :configuration ["sampleapp.wiring" "RandomNumbers"]
       :adapters ["sampleapp.wiring" "RandomNumbersAdapter"]})

The :s4-app key must include a map defining the name of the application, the namespace including the implementation of the PEs, adapters, etc. the namespace of the configuration including the wiring for the application and the name of the configuration file and, optionally, the namespace and file name for the wiring for a S4 adapter.

The S4 application bundle can be generated using the *s4-deploy* leiningen task.

> $lein bundle $DEPLOY_PATH


## License

Copyright (C) 2010 Antonio Garrote

Distributed under the Eclipse Public License, the same as Clojure.
