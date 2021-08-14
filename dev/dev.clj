(ns dev
  (:require [clj-http.client :as http]
            [jsonista.core :as json]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]))

(defonce redis-commands-url
  "https://raw.githubusercontent.com/redis/redis-doc/master/commands.json")

(defn groom-command-name [cmd]
  (-> cmd
      name
      s/lower-case
      (s/replace #" " "-")
      keyword))

(defn groom-spec [spec]
  (->> (for [[cmd doc] spec]
         [(groom-command-name cmd) doc])
       (into {})))

(defn redis-commands []
  (-> redis-commands-url
      http/get
      :body
      (json/read-value json/keyword-keys-object-mapper)
      groom-spec))

(defn make-redis-spec []
  (-> (redis-commands)
      (pprint (io/writer "resources/spec.edn"))))
