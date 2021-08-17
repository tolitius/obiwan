(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as deploy]
            [tag.core :as t]
            [clojure.pprint :refer [pprint]]))

(def lib 'tolitius/obiwan)
(def description "redis clojure client based on jedis")
(def version (format "0.1.1-SNAPSHOT"))
(def target-dir "target")
(def class-dir (str target-dir "/" "classes"))
(def jar-file (format "%s/%s-%s.jar" target-dir (name lib) version))
(def src ["src"])
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean
  "Delete the build target directory"
  [_]
  (println (str "Cleaning " target-dir))
  (b/delete {:path target-dir}))

(defn jar
  "Create the jar from a source pom and source files"
  [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :src-pom "pom.xml"
                :basis basis
                :src-dirs src})
 (-> (t/describe (str lib) {:about description})
     (t/export-intel {:app-name (str lib) :path (str class-dir "/META-INF/tag/")}))
  (b/copy-dir {:src-dirs src
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install
  "Install jar to local repo"
  [m]
  (println m)
  (b/install {:basis basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn deploy
  "Deploy jar locally or to remote artifactory"
  [{:keys [installer sign-releases?] :or {installer :local sign-releases? false}}]
  (deploy/deploy {:installer installer
                  :sign-releases? sign-releases?
                  :artifact jar-file}))
