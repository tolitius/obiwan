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
