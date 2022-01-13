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
  (is (= "PONG" (redis/say tt/conn "PING"))))

(deftest should-set-and-get
  (let [k "foo"
        v "bar"]
    (is (= "OK" (redis/set tt/conn k v)))
    (is (= v    (redis/get tt/conn k)))))

(deftest should-mset-and-mget
  (let [m {"foo" "bar"
           "baz" "moo"}]
    (is (= "OK"     (redis/mset tt/conn m)))
    (is (= (vals m) (redis/mget tt/conn (keys m))))))

(deftest should-incr-and-decr
  (let [k "meaning"
        v 42]
    (is (= "OK" (redis/set tt/conn k (str v))))
    (is (= (dec v) (redis/decr tt/conn k)))
    (is (= v (redis/incr tt/conn k)))
    (is (= 0 (redis/decr-by tt/conn k v))
    (is (= v (redis/incr-by tt/conn k v))))))

(deftest should-hset-and-hgetall
  (let [details {"nick" "the blue planet" "age" "4.543 billion years" "mass" "5974000000000000000000000"}
        earth "solar:planet:earth"]
    (is (= 3 (redis/hset tt/conn earth details)))
    (is (= details (redis/hgetall tt/conn earth)))))

(deftest should-run-commands-in-pipeline
  (let [numbers {"1" "one" "2" "two" "3" "three"}
        letters {"a" "ey" "b" "bee" "c" "cee"}
        commands [(redis/hset "numbers" numbers)
                  (redis/hset "letters" letters)
                  (redis/hgetall "numbers")
                  (redis/hgetall "letters")]]
    (is (= [(count numbers)
            (count letters)
            numbers
            letters]        (redis/pipeline tt/conn commands)))))

(deftest should-do-basic-commands
  (let [planets #{"mercury"
                  "venus"
                  "earth"
                  "mars"
                  "jupiter"
                  "saturn"
                  "uranus"
                  "neptune"
                  "pluto"}]
    (is (= (count planets) (redis/sadd tt/conn "planets" planets)))
    (is (= "set"           (redis/type tt/conn "planets")))
    (is (= 1               (redis/exists tt/conn ["planets"])))
    (is (= "planets")      (redis/keys tt/conn "plan*"))
    (is (= 1               (redis/del tt/conn ["planets"])))
    (is (= 0               (redis/exists tt/conn ["planets"])))))

(deftest should-zadd-and-zrange
  (let [planets {"mercury" 1.0
                 "venus"   2.0
                 "earth"   3.0
                 "mars"    4.0
                 "jupiter" 5.0
                 "saturn"  6.0
                 "uranus"  7.0
                 "neptune" 8.0
                 "pluto"   9.0}]
    (is (= (count planets)             (redis/zadd tt/conn "planets" planets)))
    (is (= #{"mars" "jupiter" "saturn"
             "uranus" "neptune"}       (redis/zrange tt/conn "planets" 3 7)))
    (is (= (-> planets keys set)       (redis/zrange tt/conn "planets" 0 -1)))))

(defn run-tests []
  (t/run-all-tests #"obiwan.test.*"))
