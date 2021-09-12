(ns dev
  (:require [clj-http.client :as http]
            [jsonista.core :as json]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [yang.lang :as y]
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

  ;; search core
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


  ;; aggregate shtuff

  (search/ft-create conn "website-visits"
                    {:prefix ["stats:visit:"]
                     :schema [#:text{:name "url" :sortable? true}
                              #:numeric{:name "timestamp" :sortable? true}
                              #:tag{:name "coutry" :sortable? true}
                              #:text{:name "user_id" :sortable? true :no-index? true}]})

  (def visits
    [{"url"       "/2008/11/19/zx-spectrum-child/"
      "timestamp" (str (System/nanoTime))
      "country"   "Ukraine"
      "user_id"   "tolitius"}

     {"url"       "/2017/01/10/hubble-space-mission-securely-configured/"
      "timestamp" (str (System/nanoTime))
      "country"   "China"
      "user_id"   "hashcn"}

     {"url"       "/2013/10/29/scala-where-ingenuity-lies/"
      "timestamp" (str (System/nanoTime))
      "country"   "USA"
      "user_id"   "unclejohn"}

     {"url"       "/2013/10/22/limited-async/"
      "timestamp" (str (System/nanoTime))
      "country"   "USA"
      "user_id"   "tolitius"}

     {"url"       "/2013/05/31/datomic-can-simple-be-also-fast/"
      "timestamp" (str (System/nanoTime))
      "country"   "Ukraine"
      "user_id"   "vvz"}

     {"url"       "/2012/05/08/scala-fun-with-canbuildfrom/"
      "timestamp" (str (System/nanoTime))
      "country"   "USA"
      "user_id"   "tolitius"}

     {"url"       "/2017/04/09/hazelcast-keep-your-cluster-close-but-cache-closer/"
      "timestamp" (str (System/nanoTime))
      "country"   "China"
      "user_id"   "bruce"}])

  (map-indexed (fn [idx visit]
                       (redis/hset conn
                                    (str "stats:visit:dotkam.com:" idx)
                                    visit))
               visits)

  (search/ft-aggregate conn "website-visits" "*" {:apply {:expr "@timestamp - (@timestamp % 3600000)"
                                                          :as "hour"}
                                                  :group {:by ["@hour"]
                                                          :reduce [{:fn "COUNT_DISTINCT"
                                                                    :fields ["@user_id"]
                                                                    :as "num_users"}]}
                                                  :limit {:offset 0 :number 8}})

  ;; TODO: still need a seq of "group-by"s
  (search/ft-aggregate conn "solar-system"
                            "blue | red"
                            {:group {:by ["@nick"]
                                     :reduce [{:fn "MAX"
                                               :fields ["@nick"]
                                               :as "foo"}]}
                             :limit {:offset 0
                                     :number 4}})

  ;; should probably be (or both ^^^ and \/\/\/):
  (search/ft-aggregate conn "solar-system"
                            "blue | red"
                            {:group [{:by ["@nick"]
                                      :reduce [{:fn "MAX"
                                                :fields ["@planet" "@nick @mass"]
                                                :as "nick-max"}]}
                                     {:by ["@mass"]
                                      :reduce [{:fn "MAX"
                                                :fields ["@planet" "@nick" "@mass"]
                                                :as "mass-max"}]}]
                             :limit {:offset 0
                                     :number 4}})
  ;; suggestions
  (search/ft-sugadd conn "songs" "Don't Stop Me Now" 1 {:payload "Queen"})
  (search/ft-sugadd conn "songs" "Rock You Like A Hurricane" 1 {:payload "Scorpions"})
  (search/ft-sugadd conn "songs" "Fortunate Son" 1 {:payload "Creedence Clearwater Revival"})
  (search/ft-sugadd conn "songs" "Thunderstruck" 1 {:payload "AC/DC"})
  (search/ft-sugadd conn "songs" "All Along the Watchtower" 1 {:payload "Jimmy"})
  (search/ft-sugadd conn "songs" "Enter Sandman" 1 {:payload "Metallica"})
  (search/ft-sugadd conn "songs" "Free Bird" 1 {:payload "Lynyrd Skynyrd"})
  (search/ft-sugadd conn "songs" "Immigrant Song" 1 {:payload "Led Zeppelin"})
  (search/ft-sugadd conn "songs" "Smells Like Teen Spirit" 1 {:payload "Nirvana"})
  (search/ft-sugadd conn "songs""Purple Haze" 1 {:payload "Jimmy"})

  (pprint (search/ft-sugget conn "songs" "mm" {:fuzzy? true :max 42}))

  )
