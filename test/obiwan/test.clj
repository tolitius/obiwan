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

(deftest should-hmset-a-map
  (let [details {"nick" "the blue planet" "age" "4.543 billion years" "mass" "5974000000000000000000000"}
        earth "solar:planet:earth"]
    (is "OK" (redis/hmset tt/conn earth details))
    (is details (redis/hgetall tt/conn earth))))

(defn run-tests []
  (t/run-all-tests #"obiwan.test.*"))
