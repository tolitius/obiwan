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
- [run commands in a pipeline](#run-commands-in-a-pipeline)
- [redis search](#redis-search)
  - [create the index](#create-the-index)
  - [search the index](#search-the-index)
  - [aggregations](#aggregations)
    - [apply, group by, reduce](#apply-group-by-reduce)
    - [repeating options](#repeating-options)
    - [sort by](#sort-by)
  - [work with suggestions](#work-with-suggestions)
    - [add suggestions](#add-suggestions)
    - [search suggestions](#search-suggestions)
    - [delete suggestions](#delete-suggestions)
    - [measure suggestions](#measure-suggestions)
  - [list indices](#list-indices)
  - [drop the index](#drop-the-index)
- [new redis commands](#new-redis-commands)
- [documentation](#documentation)
- [development](#development)
  - [send any redis commands](#send-any-redis-commands)
  - [run/add tests](#runadd-tests)
- [license](#license)

# connect to a cluster

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
;; #'user/conn
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

# work with data structures

the super power of redis is in its [data structures](https://redis.io/topics/data-types-intro).<br/>

here are examples on how to work with some of them:

## hash

```clojure
=> (redis/hset conn "solar-system" {"mercury"  "0.33 x 10^24 kg"
                                    "venus"    "4.867 x 10^24 kg"
                                    "earth"    "5.972 x 10^24 kg"
                                    "mars"     "0.65 x 10^24 kg"
                                    "jupiter"  "1900 x 10^24 kg"
                                    "saturn"   "570 x 10^24 kg"
                                    "uranus"   "87 x 10^24 kg"
                                    "neptune"  "100 x 10^24 kg"
                                    "pluto"    "1.3 × 10^22 kg"})
;; "OK"

=> (redis/hmget conn "solar-system" ["earth" "mars"])

;; ["5.972 x 10^24 kg" "0.65 x 10^24 kg"]

=> (redis/hgetall conn "solar-system")

;; {"earth" "5.972 x 10^24 kg",
;;  "saturn" "570 x 10^24 kg",
;;  "jupiter" "1900 x 10^24 kg",
;;  "pluto" "1.3 × 10^22 kg",
;;  "uranus" "87 x 10^24 kg",
;;  "mercury" "0.33 x 10^24 kg",
;;  "neptune" "100 x 10^24 kg",
;;  "mars" "0.65 x 10^24 kg",
;;  "venus" "4.867 x 10^24 kg"}
```

## sorted set

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
;; 9

=> (redis/zrange conn "planets" 3 7)
;; #{"mars" "jupiter" "saturn" "uranus" "neptune"}

=> (redis/zrange conn "planets" 0 -1)
;; #{"mercury" "venus" "earth" "mars" "jupiter" "saturn" "uranus" "neptune" "pluto"}
```

## what's in redis

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

take a look at [tests](test/obiwan/test.clj) to see how other commands data structures are used

# run commands in a pipeline

redis supports [pipelining](https://redis.io/topics/pipelining) to speed up client queries.

in order to run queries in a pipeline we can create a set of commands at runtime:

```clojure
=> (def commands [(redis/hset "numbers" {"1" "one" "2" "two" "3" "three"})
                  (redis/hset "letters" {"a" "ey" "b" "bee" "c" "cee"})
                  (redis/hgetall "numbers")
                  (redis/hgetall "letters")])
;; #'dev/commands
```

notice we did not pass a connection pool, but just created a few command functions

which can now be run in a single pipeline on redis servers:

```clojure
=> (redis/pipeline conn commands)
;; [3
;;  3
;;  {"1" "one", "2" "two", "3" "three"}
;;  {"a" "ey", "b" "bee", "c" "cee"}]
```

# redis search

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

> _check out [search tests](/test/obiwan/test/search.clj) to refer to all the search/aggregate/suggest/etc. examples below + more_

## create the index

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

## search the index

in order to search the index we'll first add a few documents with key prefixes matching the index prefixes:

```clojure
=> (redis/hset conn "solar:planet:earth" {"nick" "the blue planet"
                                          "age" "4.543 billion years"
                                          "mass" "5974000000000000000000000"})
=> (redis/hset conn "solar:planet:mars" {"nick" "the red planet"
                                         "age" "4.603 billion years"
                                         "mass" "639000000000000000000000"})
=> (redis/hset conn "solar:planet:pluto" {"nick" "tombaugh regio"
                                          "age" "4.5 billion years"
                                          "mass" "13090000000000000000000"})
=> (redis/hset conn "solar:planet:moon:charon" {"planet" "pluto"
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

> _Morpheus: "blue OR red?"_<br/>
> _Neo: "both"_

```clojure
=> (search/ft-search conn "solar-system"
                          "blue | red")

;; {:found 2,
;;  :results
;;  [{"solar:planet:earth"
;;    {"age" "4.543 billion years",
;;     "nick" "the blue planet",
;;     "mass" "5974000000000000000000000"}}
;;   {"solar:planet:mars"
;;    {"age" "4.603 billion years",
;;     "nick" "the red planet",
;;     "mass" "639000000000000000000000"}}]}
```

check out the full spec of the redis search native [FT.SEARCH](https://oss.redis.com/redisearch/Commands/#ftsearch) command.<br/>
also a very helpful query syntax reference in the redis search [docs](https://oss.redis.com/redisearch/Query_Syntax/).

as per spec more search params may be passed _after_ the query:

```clojure
=> (search/ft-search conn "solar-system" "red | blue"
                                         [{:sort {:by {"mass" :asc}}}])

;; {:found 2,
;;  :results
;;  [{"solar:planet:mars"
;;    {"age" "4.603 billion years",
;;     "nick" "the red planet",
;;     "mass" "639000000000000000000000"}}
;;   {"solar:planet:earth"
;;    {"age" "4.543 billion years",
;;     "nick" "the blue planet",
;;     "mass" "5974000000000000000000000"}}]}
```

```clojure
=> (search/ft-search conn "solar-system" "*"
                                         [{:sort {:by {"mass" :desc}}}
                                          {:limit {:offset 1 :number 3}}])

;; {:found 4,             ;; but showing 3 due to the limit
;;  :results
;;  [{"solar:planet:mars"
;;    {"age" "4.603 billion years",
;;     "nick" "the red planet",
;;     "mass" "639000000000000000000000"}}
;;   {"solar:planet:pluto"
;;    {"age" "4.5 billion years",
;;     "nick" "tombaugh regio",
;;     "mass" "13090000000000000000000"}}
;;   {"solar:planet:moon:charon"
;;    {"age" "4.5 billion years",
;;     "planet" "pluto",
;;     "nick" "char",
;;     "mass" "1586000000000000000000"}}]}
```

## aggregations

redis searh relies on [FT.AGGREGATE](https://oss.redis.com/redisearch/Commands/#ftaggregate) command to aggregate search query results directly on a redis server.

as per FT.AGGREGATE command's [spec](https://oss.redis.com/redisearch/Commands/#format_3) several aggreation options (group by, sort by, apply, filter, limit) may repeat many times over in the same aggregate. moreover their position matters: "apply a function => group-by => apply a different function".

therefore Obi Wan relies on a vector of these options rather than just on a map.

in order to see some examples, let's create a new search index with website visits:

```clojure
=> (search/ft-create conn "website-visits"
                     {:prefix ["stats:visit:"]
                      :schema [#:text{:name "url" :sortable? true}
                               #:numeric{:name "timestamp" :sortable? true}
                               #:tag{:name "country" :sortable? true}
                               #:text{:name "user_id" :sortable? true :no-index? true}]})
;; "OK"
```

a couple things to note (same as when we looked at FT.SEARCH above):

* for the index "schema" a namespaced key map is used and validated against existing field types redis search supports
* the whole index definition and schema is a single map that can be kept in configuration or/and sent over the network

and let's add some documents/visits to populate this index with data:

```clojure
=> (def visits
     [{"url"       "/2008/11/19/zx-spectrum-child/"
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
       "user_id"   "bruce"}])

;; #'dev/visits

=> (map-indexed (fn [idx visit]
                        (redis/hset conn
                                     (str "stats:visit:dotkam.com:" idx)
                                     visit))
                visits)

;; (4 4 4 4 4 4 4)
```

no we are ready for them tasty aggregations:

### apply, group by, reduce

first let's group users by country, counting only distinct/unique users:

```clojure
=> (search/ft-aggregate conn "website-visits" "*" [{:apply {:expr "upper(@country)"
                                                            :as "country"}}
                                                   {:group {:by ["@country"]
                                                            :reduce [{:fn "COUNT_DISTINCT"
                                                                      :fields ["@user_id"]
                                                                      :as "num_users"}]}}])
;; {:found 3,
;;  :results
;;  [{"country" "USA", "num_users" "2"}
;;   {"country" "CHINA", "num_users" "2"}
;;   {"country" "UKRAINE", "num_users" "2"}]}
```

`apply` is used here before group by to apply a function to every country before groupping to avoid casing problems, but really to illustrate how to stack options together as an ordered vector of maps.

### repeating options

here we'll use 2 applies before and after the group by to group unique website visitors per "hour":

```clojure
=>  (search/ft-aggregate conn "website-visits" "*" [{:apply {:expr "@timestamp - (@timestamp % 3600)"
                                                             :as "hour"}}
                                                    {:group {:by ["@hour"]
                                                             :reduce [{:fn "COUNT_DISTINCT"
                                                                       :fields ["@user_id"]
                                                                       :as "num_users"}]}}
                                                    {:apply {:expr "timefmt(@hour)"
                                                             :as "datetime"}}])
;; {:found 3,
;;  :results
;;  [{"num_users" "3",
;;    "hour" "1631962800",
;;    "datetime" "2021-09-18T11:00:00Z"}     ;; 11:00
;;   {"num_users" "1",
;;    "hour" "1631959200",
;;    "datetime" "2021-09-18T10:00:00Z"}     ;; 10:00
;;   {"num_users" "2",
;;    "hour" "1631966400",
;;    "datetime" "2021-09-18T12:00:00Z"}]}   ;; 12:00
```

### sort by

above example can be improved by sorting groups by time (the hour):

```clojure
=>  (search/ft-aggregate conn "website-visits" "*" [{:apply {:expr "@timestamp - (@timestamp % 3600)"
                                                             :as "hour"}}
                                                    {:group {:by ["@hour"]
                                                             :reduce [{:fn "COUNT_DISTINCT"
                                                                       :fields ["@user_id"]
                                                                       :as "num_users"}]}}
                                                    {:sort {:by {"@hour" :desc}}}
                                                    {:apply {:expr "timefmt(@hour)"
                                                             :as "datetime"}}])

;; {:found 3,
;;  :results
;;  [{"num_users" "2",
;;    "hour" "1631966400",
;;    "datetime" "2021-09-18T12:00:00Z"}     ;; 12:00
;;   {"num_users" "3",
;;    "hour" "1631962800",
;;    "datetime" "2021-09-18T11:00:00Z"}     ;; 11:00
;;   {"num_users" "1",
;;    "hour" "1631959200",
;;    "datetime" "2021-09-18T10:00:00Z"}]}   ;; 10:00
```

## work with suggestions

redis search has 4 commands to work with suggestions (a.k.a. autocomplete):

* [FT.SUGADD](https://oss.redis.com/redisearch/Commands/#ftsugadd) adds a suggestion string to an auto-complete suggestion dictionary
* [FT.SUGGET](https://oss.redis.com/redisearch/Commands/#ftsugget) gets completion suggestions for a prefix
* [FT.SUGDEL](https://oss.redis.com/redisearch/Commands/#ftsugdel) deletes a string from a suggestion index
* [FT.SUGLEN](https://oss.redis.com/redisearch/Commands/#ftsuglen) gets the size of an auto-complete suggestion dictionary

one difference from a search index is that these suggestions are left to the user to maintain: i.e. add and remove

### add suggestions

let's add a few suggestions and then try to search (or "get") them:

```clojure
=> (search/ft-sugadd conn "songs" "Don't Stop Me Now" 1 {:payload "Queen"})
   (search/ft-sugadd conn "songs" "Rock You Like A Hurricane" 1 {:payload "Scorpions"})
   (search/ft-sugadd conn "songs" "Fortunate Son" 1 {:payload "Creedence Clearwater Revival"})
   (search/ft-sugadd conn "songs" "Thunderstruck" 1 {:payload "AC/DC"})
   (search/ft-sugadd conn "songs" "All Along the Watchtower" 1 {:payload "Jimmy"})
   (search/ft-sugadd conn "songs" "Enter Sandman" 1 {:payload "Metallica"})
   (search/ft-sugadd conn "songs" "Free Bird" 1 {:payload "Lynyrd Skynyrd"})
   (search/ft-sugadd conn "songs" "Immigrant Song" 1 {:payload "Led Zeppelin"})
   (search/ft-sugadd conn "songs" "Smells Like Teen Spirit" 1 {:payload "Nirvana"})
   (search/ft-sugadd conn "songs" "Purple Haze" 1 {:payload "Jimmy"})
```

following the redis command [spec](https://oss.redis.com/redisearch/Commands/#ftsugadd), besides a redis connection pool,
at a minimum `ft-sugadd` takes:

* `key`    : the suggestion dictionary key
* `string` : the suggestion string we index
* `score`  : a floating point number of the suggestion string's weight

and will take optional args:

* `incr?`         : if true, we increment the existing entry of the suggestion by the given score, instead of replacing the score. This is useful for updating the dictionary based on user queries in real time
* `payload value` : If set, we save an extra payload with the suggestion, that can be fetched by adding the WITHPAYLOADS argument to FT.SUGGET

### search suggestions

now let's search through the suggestions:

```clojure
=> (search/ft-sugget conn "songs" "don")
;; [{:suggestion "Don't Stop Me Now"}]
```

by [the spec](https://oss.redis.com/redisearch/Commands/#ftsugget) `ft-get` optinally also takes:

* `fuzzy?`          : if true, we do a fuzzy prefix search, including prefixes at Levenshtein distance of 1 from the prefix sent
* `max` num         : if set, we limit the results to a maximum of num (default: 5).
* `with-scores?`    : if true, we also return the score of each suggestion. this can be used to merge results from multiple instances
* `with-payloads?`  : if true, we return optional payloads saved along with the suggestions.

let's try them all together:

```clojure
=> (search/ft-sugget conn "songs" "mm" {:fuzzy? true
                                        :with-payloads? true
                                        :with-scores? true
                                        :max 42}))
;; [{:suggestion "Immigrant Song",
;;   :score "0.037535253912210464",
;;   :payload "Led Zeppelin"}
;;  {:suggestion "Smells Like Teen Spirit",   ;; Levenshtein distance of 1 "mm" => "m"
;;   :score "0.028853578492999077",
;;   :payload "Nirvana"}]
```

### delete suggestions

suggestions can be deleted with `ft-sugdel`:

```clojure
=> (search/ft-sugdel conn "songs" "Fortunate Son")
;; 1
```

### measure suggestions

suggestions can be "measured" (how many suggestions live behind the key):

```clojure
=> (search/ft-suglen conn "songs")
;; 9
```

## list indices

to list search indices:

```clojure
=> (search/ft-list conn)
;; #{"solar-system"}
```

## drop the index

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

# new redis commands

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

# documentation

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

# development

while Obi Wan does not require any particular version of clojure to run<br/>
since its build is done via [tools.build](https://clojure.org/guides/tools_build)<br/>
the minimum [1.10.3](https://clojure.org/releases/downloads#_stable_release_1_10_3_mar_4_2021) clojure CLI is recommended.

to fire up a development REPL:

```bash
make repl
```

## send any redis commands

this is usefult to experiment with various redis commands to see what they return, how to parse the responses as well as an ability to run any redis commands that may not be yet supported / wrapped in a clojure function.

```clojure
=> (require '[obiwan.core :as redis]
            '[obiwan.tools :as t])

=> (def conn (redis/create-pool))
```

```clojure
=> (redis/say conn "PING")
;; "PONG"

=> (redis/say conn "ECHO" {:args "HAYA!"})
;; "HAYA!"

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
;; 264
```
```clojure
=> (redis/say conn "DBSIZE" {:expect t/integer-reply})
;; 42
```

## run/add tests

```bash
$ make test
```

when running tests, make sure the [embedded redis config](test/resources/config.edn#L4) matches your OS.
as well as a path to redis server and redis modules.

# license

Copyright © 2021 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
