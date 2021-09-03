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
