# obi wan

> _"these aren't the droids you're looking for"_

[![<! release](https://img.shields.io/badge/dynamic/json.svg?label=release&url=https%3A%2F%2Fclojars.org%2Fcom.tolitius%2Fobiwan%2Flatest-version.json&query=version&colorB=blue)](https://github.com/tolitius/obiwan/releases)
[![<! clojars>](https://img.shields.io/clojars/v/com.tolitius/obiwan.svg)](https://clojars.org/com.tolitius/obiwan)

redis/search clojure client based on [jedis](https://github.com/redis/jedis).

- [connect to a cluster](#connect-to-a-cluster)
- [work with data structures](#work-with-data-structures)
  - [hash](#hash)
  - [sorted set](#sorted-set)
  - [what's in redis](#whats-in-redis)
- [redis search](#redis-search)
  - [create the index](#create-the-index)
  - [search the index](#search-the-index)
  - [drop the index](#drop-the-index)
  - [list indices](#list-indices)
- [new redis commands](#new-redis-commands)
- [documentation](#documentation)
- [development](#development)
  - [send any redis commands](#send-any-redis-commands)
- [license](#license)

## connect to a cluster

Obi Wan relies on [JedisPool](https://www.javadoc.io/doc/redis.clients/jedis/latest/redis/clients/jedis/JedisPool.html) to connect to a cluster
which means once the pool is created you'd have a collection of connections to work with.

Let's try it out:

```clojure
=> (require '[obiwan.core :as redis])

;; by default would connect to a local redis 127.0.0.1:6379 with a 42 thread connection pool
=> (def conn (redis/create-pool))
#'user/conn

=> (redis/pool-stats conn)
;; {:active-resources 0,
;;  :number-of-waiters 0,
;;  :idle-resources 0}
```

in order to connect to a different host, port, with a different number of threads, password, etc. `create-pool` takes a config:

```clojure
=> (def conn (redis/create-pool {:host "dotkam.com"
                                 :port 4242
                                 :timeout 42000
                                 :pool {:size 4
                                        :max-wait 15000}
                                 :password "|th1s is the w@y|"}))
#'user/conn
```

by default the config map is:

```clojure
{:host "127.0.0.1"
 :port 6379
 :timeout Protocol/DEFAULT_TIMEOUT ;; 2 seconds as per Jedis' protocol
 :pool {:size 42
        :max-wait 30000}}
```

closing the pool:

```clojure
=> (redis/close-pool conn)
;; :pool-closed
```

## work with data structures

the super power of redis is in its [data structures](https://redis.io/topics/data-types-intro).<br/>

here are examples on how to work with some of them:

### hash

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

### sorted set

```clojure
=> (redis/zadd conn "planets" {"mercury" 1.0
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

### what's in redis

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

## redis search

redis comes with several great [modules](https://redis.io/modules).<br/>
[redis search](https://github.com/RediSearch/RediSearch) adds querying, secondary indexing, full text, geo search, etc.. it's pretty great.

Obi Wan does not depend on anything but [Jedis proper](https://github.com/redis/jedis) and follows the redis search [command reference](https://oss.redis.com/redisearch/Commands/) to implement the redis search module functionality.

in order to play with redis search, or other redis modules, make sure they are [installed and run](https://oss.redis.com/redisearch/Quick_Start/) them with your redis server.

and embrace the force of redis search:

```clojure
=> (require '[obiwan.core :as redis]
            '[obiwan.search.core :as search])

=> (def conn (redis/create-pool))
```

### create the index

in order to let redis know it needs to start building a search index based on certain key prefix(es)
this search index needs to be created:

```clojure
=> (search/ft-create conn "solar-system"
                     {:prefix ["solar:planet:" "solar:planet:moon:"]
                      :schema [#:text{:name "nick" :sortable? true}
                               #:text{:name "age" :no-index? true}
                               #:numeric{:name "mass" :sortable? true}]})
```

a couple things to note:

* for the index "schema" a namespaced key map is used and validated against existing field types redis search supports
* the whole index definition and schema is a single map that can be kept in configuration or/and sent over the network

here is the full spec of the redis search native [FT.CREATE](https://oss.redis.com/redisearch/Commands/#ftcreate) command.

### search the index

in order to search the index we'll first add a few documents with key prefixes matching the index prefixes:

```clojure
=> (redis/hmset conn "solar:planet:earth" {"nick" "the blue planet"
                                           "age" "4.543 billion years"
                                           "mass" "5974000000000000000000000"})
=> (redis/hmset conn "solar:planet:mars" {"nick" "the red planet"
                                          "age" "4.603 billion years"
                                          "mass" "639000000000000000000000"})
=> (redis/hmset conn "solar:planet:pluto" {"nick" "tombaugh regio"
                                           "age" "4.5 billion years"
                                           "mass" "13090000000000000000000"})
=> (redis/hmset conn "solar:planet:moon:charon" {"planet" "pluto"
                                                 "nick" "char"
                                                 "age" "4.5 billion years"
                                                 "mass" "1586000000000000000000"})
```

and.. we'll use `ft-search` function to search:

```clojure
(search/ft-search conn "solar-system"
                       "@nick:re*")

;; {:found 2,
;;  :results
;;  [{"solar:planet:mars"
;;    {"age" "4.603 billion years",
;;     "nick" "the red planet",
;;     "mass" "639000000000000000000000"}}
;;   {"solar:planet:pluto"
;;    {"age" "4.5 billion years",
;;     "nick" "tombaugh regio",
;;     "mass" "13090000000000000000000"}}]}
```

this is the full spec of the redis search native [FT.SEARCH](https://oss.redis.com/redisearch/Commands/#ftsearch) command.<br/>
also check out a very helpful query syntax reference in the redis search [docs](https://oss.redis.com/redisearch/Query_Syntax/).

### list indices

to list search indices:

```clojure
=> (search/ft-list conn)
#{"solar-system"}
```

### drop the index

to drop an index:

```clojure
=> (search/ft-drop-index conn "solar-system")
;; "OK"
```

or to also delete indexed hashes:

```clojure
=> (search/ft-drop-index conn "solar-system" {:dd? true})
;; "OK"
```

## new redis commands

being a Jedi, Obi Wan knows the way of the force<br/>
even when "Jedis" is [not yet upto date](https://github.com/redis/jedis/issues/2581) Obi Wan can run new Redis commands:

```clojure
=> (redis/hello conn)

;; {"role" "master",
;;  "server" "redis",
;;  "modules" [{"name" "search",
;;              "ver" 999999}],
;;  "id" 23,
;;  "mode" "standalone",
;;  "version" "6.2.5",
;;  "proto" 2}
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

## development

while Obi Wan does not require any particular version of Clojure to run<br/>
since it's build is done via [tools.build](https://clojure.org/guides/tools_build)<br/>
the minimum [1.10.3](https://clojure.org/releases/downloads#_stable_release_1_10_3_mar_4_2021) Clojure CLI is recommended.

to fire up a development REPL:

```bash
make repl
```

### send any redis commands

this is usefult to experiment with various redis commands to see what they return, how to parse the responses as well as an ability to run any redis commands that may not be yet supported / wrapped in a clojure function.

```clojure
=> (redis/say conn "PING")
;; "PONG"

=> (redis/say conn "ECHO" {:args "HAYA!"})
"HAYA!"

```
```clojure
=> (print (redis/say conn "INFO"))

;; # Server
;; redis_version:6.2.5
;; redis_git_sha1:00000000
;; redis_git_dirty:0
;; ...
;; # CPU
;; used_cpu_sys:84.315657
;; used_cpu_user:50.403381
;; ...
;; # Modules
;; module:name=search,ver=999999,api=1,filters=0,usedby=[],using=[],options=[]
```

parsing replies with `:expect` function:

```clojure
=> (redis/say conn "COMMAND" {:args "COUNT"
                              :expect t/integer-reply})
264
```
```clojure
=> (redis/say conn "DBSIZE" {:expect t/integer-reply})
42
```

## license

Copyright © 2021 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
