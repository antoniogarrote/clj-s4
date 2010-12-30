(ns clj-s4.utils)

(defn show-java-methods
  "Collections and optionally prints the methods defined in a Java object"
  ([obj should-show?]
     (let [ms (.. obj getClass getDeclaredMethods)
           max (alength ms)]
       (loop [count 0
              acum []]
         (if (< count max)
           (let [m (aget ms count)
                 params (.getParameterTypes m)
                 params-max (alength params)
                 return-type (.getReturnType m)
                 to-show (str (loop [acum (str (.getName m) "(")
                                     params-count 0]
                                (if (< params-count params-max)
                                  (recur (str acum " " (aget params params-count))
                                         (+ params-count 1))
                                  acum))
                              " ) : " return-type)]
             (when should-show? (println (str to-show)))
             (recur (+ 1 count)
                    (conj acum (str to-show))))
           acum))))
  ([obj] (show-java-methods obj true)))
