# 0.1.4805

* add defaults to jedis pool config

# 0.1.4804

* jedis dep to 3.9.0
* expose `min-evictable-idle-time` connection pool prop

# 0.1.475

* add APPLY to FT.AGGREGATE
* fix FT.CREATE "TAG" refence

# 0.1.474

* add module load/unload commands
* add search test harness and search / suggest tests

# 0.1.473

* convert all supported core commands to support pipelining
* `mset` takes a map vs. a seq of kv pairs
* fix the `(is foo bar)` => `(is (= foo bar))` tests

# 0.1.46

add pipelining:

```clojure
(redis/pipeline conn [(redis/hset ...)
                      (redis/hmget...)
                      (redis/hset ...)
                      ...])
```

so far only to hashes
TODO: add to all

# 0.1.45

added more basic commands:

* del, exists, type, keys
* set, get
* mset, mget
* incr, incr-by
* decr, decr-by

# 0.1.43

remove the "tools.logging" dep<br/>
leaving jedis to be the only dep

# 0.1.42

add suggestion commands:

* FT.SUGADD
* FT.SUGGET
* FT.SUGDEL
* FT.SUGLEN
