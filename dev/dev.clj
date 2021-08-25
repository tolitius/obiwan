(ns dev
  (:require [clj-http.client :as http]
            [jsonista.core :as json]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [spec :as rspec]
            [obiwan.core :as redis]
            [obiwan.tools :as t]
            [obiwan.search.core :as search]))

(def spec (rspec/slurp-redis-spec))

(defn ns-fns [nspace]
 (let [raw (-> nspace symbol ns-publics)]
   (->> (for [[k v] raw]
          [(keyword k) v])
        (into {}))))

;;TODO: add as a hook on building a jar
(defn add-redis-docs [nspace]
  (doseq [[fname path] (ns-fns nspace)]
    (when-let [doc (fname spec)]
      (alter-meta! path
                   assoc-in [:doc :redis-doc] doc))))

(comment

  ;; repl

  (require '[obiwan.core :as redis]
           '[obiwan.tools :as t]
           '[obiwan.search.command.create :as cc]
           '[obiwan.search.command.search :as cs]
           '[obiwan.search.command.core :as cmd])

  (require '[obiwan.core :as redis]
           '[obiwan.search.core :as search])

  (def conn (redis/create-pool))

  (search/ft-create conn "solar-system"
                    {:prefix ["solar:planet:" "solar:planet:moon:"]
                     :schema [#:text{:name "nick" :sortable? true}
                              #:text{:name "age" :no-index? true}
                              #:numeric{:name "mass" :sortable? true}]})

  (redis/hmset conn "solar:planet:earth" {"nick" "the blue planet" "age" "4.543 billion years" "mass" "5974000000000000000000000"})
  (redis/hmset conn "solar:planet:mars" {"nick" "the red planet" "age" "4.603 billion years" "mass" "639000000000000000000000"})
  (redis/hmset conn "solar:planet:pluto" {"nick" "tombaugh regio" "age" "4.5 billion years" "mass" "13090000000000000000000"})
  (redis/hmset conn "solar:planet:moon:charon" {"planet" "pluto" "nick" "char" "age" "4.5 billion years" "mass" "1586000000000000000000"})

  (search/ft-search conn "solar-system"
                         "@nick:re*")
  )
