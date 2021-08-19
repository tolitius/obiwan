(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as deploy]
            [tag.core :as t]
            [clojure.pprint :refer [pprint]]))

;; TODO: taking tools.build for a spin

(defonce project
  (let [lib        'com.tolitius/obiwan
        version    "0.1.2-SNAPSHOT"
        target-dir "target"]
    {:lib lib
     :description "redis clojure client based on jedis"
     :version version
     :src-dir ["src"]
     :target-dir target-dir
     :class-dir (str target-dir "/" "classes")
     :jar-file (format "%s/%s-%s.jar"
                       target-dir (name lib) version)
     :basis (b/create-basis {:project "deps.edn"})}))

(defn clean
  "delete the build target directory"
  [_]
  (let [{:keys [target-dir]} project]
    (println (str "cleaning " target-dir))
    (b/delete {:path target-dir})))

(defn jar
  "create the jar from a source pom and source files"
  [_]
  (let [{:keys [lib description src-dir class-dir jar-file]} project]
    (b/write-pom project)
    (-> (t/describe (str lib)
                    {:about description})
        (t/export-intel {:app-name (str lib)
                         :path (str class-dir "/META-INF/tag/")}))
    (b/copy-dir {:src-dirs src-dir
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

(defn install
  "install jar to local repo"
  [m]
  (println m)
  (b/install project))

(defn deploy
  "deploy jar locally or to remote artifactory"
  [{:keys [installer sign-releases?]
    :or {installer :remote
         sign-releases? true}}]
  (let [{:keys [jar-file class-dir lib version]} project
        ;; pom.xml is currently written by tools.build to
        ;; https://github.com/clojure/tools.build/blob/1fcbe869c988d1d5f9e7d2518bdb4fa2391ccfca/src/main/clojure/clojure/tools/build/tasks/write_pom.clj#L236
        pom-file (str class-dir "/META-INF/maven/" (namespace lib) "/" (name lib) "/pom.xml")
        pom-asc  (str (name lib) "-" version ".pom.asc")]

    (deploy/deploy {:installer installer
                    :sign-releases? sign-releases?
                    :pom-file pom-file
                    :artifact jar-file})
    (b/delete {:path pom-asc})))
