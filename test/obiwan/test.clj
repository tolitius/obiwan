(ns obiwan.test
  (:require [clojure.test :as t :refer [deftest is]]
            [obiwan.test.tools :as tt]
            [obiwan.core :as redis]
            obiwan.search.core
            obiwan.test.search))

(t/use-fixtures :once tt/with-redis tt/with-connection-pool)
(t/use-fixtures :each tt/with-flushall)

(deftest should-connect-to-redis
  (is (redis/connected? tt/conn)))

(deftest should-ping-pong
  (is (redis/say tt/conn "PING") "PONG"))

(deftest should-set-and-get
  (let [k "foo"
        v "bar"]
    (is "OK" (redis/set tt/conn k v))
    (is v (redis/get tt/conn k))))

(deftest should-mset-and-mget
  (let [ks ["foo" "baz"]
        vs ["bar" "moo"]]
    (is "OK" (redis/mset tt/conn (->> [ks vs]
                                      (map vector)
                                      flatten)))
    (is vs (redis/mget tt/conn ks))))

(deftest should-incr-and-decr
  (let [k "meaning"
        v 42]
    (is "OK" (redis/set tt/conn k (str v)))
    (is (dec v) (redis/decr tt/conn k))
    (is v (redis/incr tt/conn k))
    (is 0 (redis/decr-by tt/conn k v))
    (is v (redis/incr-by tt/conn k v))))

(deftest should-hset-and-hgetall
  (let [details {"nick" "the blue planet" "age" "4.543 billion years" "mass" "5974000000000000000000000"}
        earth "solar:planet:earth"]
    (is "OK" (redis/hset tt/conn earth details))
    (is details (redis/hgetall tt/conn earth))))

(deftest should-run-commands-in-pipeline
  (let [numbers {"1" "one" "2" "two" "3" "three"}
        letters {"a" "ey" "b" "bee" "c" "cee"}
        commands [(redis/hset "numbers" numbers)
                  (redis/hset "letters" letters)
                  (redis/hgetall "numbers")
                  (redis/hgetall "letters")]]
    (is [(count numbers)
         (count letters)
         numbers
         letters]        (redis/pipeline tt/conn commands))))

(defn run-tests []
  (t/run-all-tests #"obiwan.test.*"))
