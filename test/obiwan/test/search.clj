(ns obiwan.test.search
  (:require [clojure.test :as t :refer [deftest is]]
            [obiwan.test.tools :as tt]
            [obiwan.core :as redis]
            [obiwan.search.core :as search]))

(t/use-fixtures :once tt/with-redis
                      tt/with-connection-pool
                      tt/with-search-module)
(t/use-fixtures :each tt/with-flushall)

(defn make-solar-system [conn]
  (search/ft-create conn "solar-system"
                    {:prefix ["solar:planet:" "solar:planet:moon:"]
                     :schema [#:text{:name "nick" :sortable? true}
                              #:text{:name "age" :no-index? true}
                              #:numeric{:name "mass" :sortable? true}]})
  (redis/hmset conn "solar:planet:earth"
               {"nick" "the blue planet" "age" "4.543 billion years" "mass" "5974000000000000000000000"})
  (redis/hmset conn "solar:planet:mars"
               {"nick" "the red planet" "age" "4.603 billion years" "mass" "639000000000000000000000"})
  (redis/hmset conn "solar:planet:pluto"
               {"nick" "tombaugh regio" "age" "4.5 billion years" "mass" "13090000000000000000000"})
  (redis/hmset conn "solar:planet:moon:charon"
               {"planet" "pluto" "nick" "char" "age" "4.5 billion years" "mass" "1586000000000000000000"}))

(deftest should-full-text-search-star
  (make-solar-system tt/conn)
  (is (= {:found 2
          :results [{"solar:planet:mars" {"age" "4.603 billion years"
                                          "nick" "the red planet"
                                          "mass" "639000000000000000000000"}}
                    {"solar:planet:pluto" {"age" "4.5 billion years"
                                           "nick" "tombaugh regio"
                                           "mass" "13090000000000000000000"}}]}
         (search/ft-search tt/conn "solar-system" "@nick:re*"))))

(deftest should-full-text-search-or
  (make-solar-system tt/conn)
  (is (= {:found 2
          :results [{"solar:planet:earth" {"age" "4.543 billion years"
                                           "nick" "the blue planet"
                                           "mass" "5974000000000000000000000"}}
                    {"solar:planet:mars" {"age" "4.603 billion years"
                                          "nick" "the red planet"
                                          "mass" "639000000000000000000000"}}]}
         (search/ft-search tt/conn "solar-system" "red | blue"))))


;; suggest

(defn make-suggestion-dictionary [conn]
  (search/ft-sugadd conn "songs" "Don't Stop Me Now" 1 {:payload "Queen"})
  (search/ft-sugadd conn "songs" "Rock You Like A Hurricane" 1 {:payload "Scorpions"})
  (search/ft-sugadd conn "songs" "Fortunate Son" 1 {:payload "Creedence Clearwater Revival"})
  (search/ft-sugadd conn "songs" "Thunderstruck" 1 {:payload "AC/DC"})
  (search/ft-sugadd conn "songs" "All Along the Watchtower" 1 {:payload "Jimmy"})
  (search/ft-sugadd conn "songs" "Enter Sandman" 1 {:payload "Metallica"})
  (search/ft-sugadd conn "songs" "Free Bird" 1 {:payload "Lynyrd Skynyrd"})
  (search/ft-sugadd conn "songs" "Immigrant Song" 1 {:payload "Led Zeppelin"})
  (search/ft-sugadd conn "songs" "Smells Like Teen Spirit" 1 {:payload "Nirvana"})
  (search/ft-sugadd conn "songs" "Purple Haze" 1 {:payload "Jimmy"}))

(deftest should-search-suggest
  (make-suggestion-dictionary tt/conn)
  (is (= [{:suggestion "Immigrant Song"} {:suggestion "Smells Like Teen Spirit"}]
         (search/ft-sugget tt/conn "songs" "mm" {:fuzzy? true :max 42}))))


;; aggregate

(defn make-website-visit-index [conn]

  (search/ft-create conn "website-visits"
                    {:prefix ["stats:visit:"]
                     :schema [#:text{:name "url" :sortable? true}
                              #:numeric{:name "timestamp" :sortable? true}
                              #:tag{:name "country" :sortable? true}
                              #:text{:name "user_id" :sortable? true :no-index? true}]})

  (let [visits [{"url"       "/2008/11/19/zx-spectrum-child/"
                 "timestamp" "1631965013"
                 "country"   "Ukraine"
                 "user_id"   "tolitius"}

                {"url"       "/2017/01/10/hubble-space-mission-securely-configured/"
                 "timestamp" "1631963013"
                 "country"   "China"
                 "user_id"   "hashcn"}

                {"url"       "/2013/10/29/s1631982013728cala-where-ingenuity-lies/"
                 "timestamp" "1631964013"
                 "country"   "USA"
                 "user_id"   "unclejohn"}

                {"url"       "/2013/10/22/l1631982013728imited-async/"
                 "timestamp" "1631965013"
                 "country"   "USA"
                 "user_id"   "tolitius"}

                {"url"       "/2013/05/31/d1631982013728atomic-can-simple-be-also-fast/"
                 "timestamp" "1631967013"
                 "country"   "Ukraine"
                 "user_id"   "vvz"}

                {"url"       "/2012/05/08/s1631982013728cala-fun-with-canbuildfrom/"
                 "timestamp" "1631968013"
                 "country"   "USA"
                 "user_id"   "tolitius"}

                {"url"       "/2017/04/09/h1631982013728azelcast-keep-your-cluster-close-but-cache-closer/"
                 "timestamp" "1631961014"
                 "country"   "China"
                 "user_id"   "bruce"}]]

    (-> (map-indexed (fn [idx visit]
                       (redis/hset conn
                                   (str "stats:visit:dotkam.com:" idx)
                                   visit))
                     visits)
        doall)))

(deftest should-apply-group-by-and-reduce
  (make-website-visit-index tt/conn)
  (is (= {:found 3
          :results [{"country" "USA", "num_users" "2"}
                    {"country" "CHINA", "num_users" "2"}
                    {"country" "UKRAINE", "num_users" "2"}]}
         (search/ft-aggregate tt/conn "website-visits" "*" [{:apply {:expr "upper(@country)"
                                                                     :as "country"}}
                                                            {:group {:by ["@country"]
                                                                     :reduce [{:fn "COUNT_DISTINCT"
                                                                               :fields ["@user_id"]
                                                                               :as "num_users"}]}}]))))

(deftest should-wrap-group-by-in-applies
  (make-website-visit-index tt/conn)
  (is (= {:found 3,
          :results [{"num_users" "3",
                     "hour" "1631962800",
                     "datatime" "2021-09-18T11:00:00Z"}     ;; 11:00
                    {"num_users" "1",
                     "hour" "1631959200",
                     "datatime" "2021-09-18T10:00:00Z"}     ;; 10:00
                    {"num_users" "2",
                     "hour" "1631966400",
                     "datatime" "2021-09-18T12:00:00Z"}]}   ;; 12:00
         (search/ft-aggregate tt/conn "website-visits" "*" [{:apply {:expr "@timestamp - (@timestamp % 3600)"
                                                                     :as "hour"}}
                                                            {:group {:by ["@hour"]
                                                                     :reduce [{:fn "COUNT_DISTINCT"
                                                                               :fields ["@user_id"]
                                                                               :as "num_users"}]}}
                                                            {:apply {:expr "timefmt(@hour)"
                                                                     :as "datatime"}}]))))

(deftest should-wrap-sort-the-group
  (make-website-visit-index tt/conn)
  (is (= {:found 3,
          :results [{"num_users" "2",
                     "hour" "1631966400",
                     "datatime" "2021-09-18T12:00:00Z"}     ;; 12:00
                    {"num_users" "3",
                     "hour" "1631962800",
                     "datatime" "2021-09-18T11:00:00Z"}     ;; 11:00
                    {"num_users" "1",
                     "hour" "1631959200",
                     "datatime" "2021-09-18T10:00:00Z"}]}   ;; 10:00
         (search/ft-aggregate tt/conn "website-visits" "*" [{:apply {:expr "@timestamp - (@timestamp % 3600)"
                                                                     :as "hour"}}
                                                            {:group {:by ["@hour"]
                                                                     :reduce [{:fn "COUNT_DISTINCT"
                                                                               :fields ["@user_id"]
                                                                               :as "num_users"}]}}
                                                            {:sort {:by {"@hour" :desc}}}
                                                            {:apply {:expr "timefmt(@hour)"
                                                                     :as "datatime"}}]))))
