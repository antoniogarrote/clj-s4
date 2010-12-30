(ns leiningen.s4-deploy
  "Deploys a S4 application"
  (:use leiningen.jar)
  (:use [clojure.java.io :only [copy]])
  (:use leiningen.util.file)
  (:use clj-s4.wiring)
  (:import (java.io File)))

(def *default-deploy-dir* "s4")

(defn- make-s4-directory
  ([project deploy-path]
     (let [current-s4 (File. deploy-path)]
       (when-not (.exists current-s4)
         (println (str "*** creating deploy dir " current-s4))
         (.mkdir current-s4)))))

(defn- make-app-directory
  ([project deploy-path app-file-name]
     (let [current-app (File. (str deploy-path "/" app-file-name))
           lib-dir (File. (str deploy-path "/" app-file-name "/lib"))]
       (when (.exists current-app)
         (println (str "*** deleting old S4 app dir " current-app))
         (delete-file-recursively current-app))
       (.mkdir current-app)
       (.mkdir lib-dir))))

(defn- generate-component-wiring
  ([app-directory file-name wiring-ns component-id]
     (use (symbol wiring-ns))
     (let [xml (gen-xml component-id)]
       (spit (str app-directory "/" file-name) xml))))

(defn- copy-jars
  ([from to]
     (let [from-dir (File. from)
           jar-files (map (fn [fp] (File. (str from-dir "/" fp))) (filter(fn [fp] (.endsWith fp ".jar")) (.list from-dir)))]
       (doseq [jar-file jar-files]
         (let [name (.getName jar-file)
               to-file (File. (str to "/" name))]
           (println (str "  - packaging dependency " (.getName to-file)))
           (copy jar-file to-file))))))

(defn s4-deploy
  ([project] (s4-deploy project (str (:root project) "/" *default-deploy-dir*)))
  ([project deploy-path]
     (println (str "*** compiling project and creating jar"))
     (leiningen.jar/jar project (get-default-jar-name project))
     (let [app (:s4-app project)
           jar-filename (get-jar-filename project (get-default-jar-name project))]
       (if (nil? app)
         (println (str "S4 app not found in the project file"))
         (let [app-name (:name app)
               namespace (:namespace app)
               configuration (:configuration app)
               adapters (:adapters app)
               s4-app-path (str deploy-path "/" app-name)]
           (make-s4-directory project deploy-path)
           (make-app-directory project deploy-path app-name)
           (let [[wiring-ns wiring-comp-id] configuration]
             (println (str "*** generating wiring xml for S4 app " (str app-name "_conf.xml")))
             (generate-component-wiring s4-app-path (str app-name "_conf.xml") wiring-ns wiring-comp-id))
           (when (not (nil? adapters))
             (let [[wiring-ns wiring-comp-id] adapters]
               (println (str "*** generating adapter wiring xml for S4 app adapter_conf.xml"))
               (generate-component-wiring s4-app-path "adapter_conf.xml" wiring-ns wiring-comp-id)))
           (copy-jars (str (:root project) "/lib") (str s4-app-path "/lib"))
           (println (str "*** packaging application JAR fie " (get-default-jar-name project)))
           (copy (File. jar-filename) (File. (str s4-app-path "/lib/" (get-default-jar-name project))))
           (println (str "* Application deployed")))))))
