# obi wan

> _"These aren't the droids you're looking for"_

[![<! release](https://img.shields.io/badge/dynamic/json.svg?label=release&url=https%3A%2F%2Fclojars.org%2Fcom.tolitius%2Fobiwan%2Flatest-version.json&query=version&colorB=blue)](https://github.com/tolitius/obiwan/releases)
[![<! clojars>](https://img.shields.io/clojars/v/com.tolitius/obiwan.svg)](https://clojars.org/com.tolitius/obiwan)

redis clojure client based on [jedis](https://github.com/redis/jedis).

- [spilling the beans](#spilling-the-beans)
- [new redis commands](#new-redis-commands)
- [documentation](#documentation)
- [license](#license)

## spilling the beans

```clojure
$ make repl

=> (require '[obiwan.core :as redis])

;; by default would connect to a local redis 127.0.0.1:6379 with a 42 thread connection pool
=> (def conn (redis/create-pool))
#'user/conn

=> (redis/pool-stats conn)
{:active-resources 0, :number-of-waiters 0, :idle-resources 0}
```

hash

```clojure
=> (redis/hmset conn "solar-system" {"mercury"  "0.33 x 10^24 kg"
                                     "venus"    "4.867 x 10^24 kg"
                                     "earth"    "5.972 x 10^24 kg"
                                     "mars"     "0.65 x 10^24 kg"
                                     "jupiter"  "1900 x 10^24 kg"
                                     "saturn"   "570 x 10^24 kg"
                                     "uranus"   "87 x 10^24 kg"
                                     "neptune"  "100 x 10^24 kg"
                                     "pluto"    "1.3 × 10^22 kg"})
"OK"

=> (redis/hmget conn "solar-system" ["earth" "mars"])
["5.972 x 10^24 kg" "0.65 x 10^24 kg"]

=> (redis/hgetall conn "solar-system")
{"earth" "5.972 x 10^24 kg",
 "saturn" "570 x 10^24 kg",
 "jupiter" "1900 x 10^24 kg",
 "pluto" "1.3 × 10^22 kg",
 "uranus" "87 x 10^24 kg",
 "mercury" "0.33 x 10^24 kg",
 "neptune" "100 x 10^24 kg",
 "mars" "0.65 x 10^24 kg",
 "venus" "4.867 x 10^24 kg"}
```

sorted set

```clojure
user=> (redis/zadd conn "planets" {"mercury" 1.0
                                   "venus"   2.0
                                   "earth"   3.0
                                   "mars"    4.0
                                   "jupiter" 5.0
                                   "saturn"  6.0
                                   "uranus"  7.0
                                   "neptune" 8.0
                                   "pluto"   9.0})
9

=> (redis/zrange conn "planets" 3 7)
#{"mars" "jupiter" "saturn" "uranus" "neptune"}

=> (redis/zrange conn "planets" 0 -1)
#{"mercury" "venus" "earth" "mars" "jupiter" "saturn" "uranus" "neptune" "pluto"}
```

looking inside the source (redis server):

```bash
127.0.0.1:6379> keys *
1) "planets"
2) "solar-system"

127.0.0.1:6379> hgetall "solar-system"
 1) "mars"
 2) "0.65 x 10^24 kg"
 3) "pluto"
 4) "1.3 \xc3\x97 10^22 kg"
 5) "jupiter"
 6) "1900 x 10^24 kg"
 7) "uranus"
 8) "87 x 10^24 kg"
 9) "earth"
10) "5.972 x 10^24 kg"
11) "mercury"
12) "0.33 x 10^24 kg"
13) "venus"
14) "4.867 x 10^24 kg"
15) "saturn"
16) "570 x 10^24 kg"
17) "neptune"
18) "100 x 10^24 kg"

127.0.0.1:6379> zrange planets 0 -1
1) "mercury"
2) "venus"
3) "earth"
4) "mars"
5) "jupiter"
6) "saturn"
7) "uranus"
8) "neptune"
9) "pluto"
127.0.0.1:6379>
```

## new redis commands

being a Jedi Obi Wan knows the way of the force
even when "Jedis" is [not yet upto date](https://github.com/redis/jedis/issues/2581) supporting new Redis commands:

```clojure
dev=> (redis/hello conn)
{"role" "master",
 "server" "redis",
 "modules" [],
 "id" 571,
 "mode" "standalone",
 "version" "6.2.5",
 "proto" 2}
```

## documentation

redis command documentation can be added via `dev/add-redis-docs` function:

```clojure
=> (doc redis/hget)
;; -------------------------
;; obiwan.core/hget
;; ([redis h f])
;;   {:obiwan-doc takes in a jedis connection pool, a hash name and a field name
;;                if present, returns a field name value}

=> (require '[dev :refer :all])

=> (add-redis-docs "obiwan.core")

=> (doc redis/hget)
;; -------------------------
;; obiwan.core/hget
;; ([redis h f])
;;   {:obiwan-doc takes in a jedis connection pool, a hash name and a field name
;;                if present, returns a field name value
;;    :redis-doc {:since 2.0.0,
;;                :group hash,
;;                :arguments [{:name key, :type key}
;;                            {:name field, :type string}],
;;                :complexity O(1),
;;                :summary Get the value of a hash field}}
```

## license

Copyright © 2021 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
