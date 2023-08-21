# 0.2.0

## full refactor to support [UnifiedJedis](https://javadoc.io/static/redis.clients/jedis/5.0.0-beta2/redis/clients/jedis/UnifiedJedis.html)

which is a stepping stone to JedisCluster, JedisPooled, JedisSentineled, JedisSharding

a few braking changes (hence the minor version bump):

### connecting / disconnecting

pre `0.2.x`:
```clojure
=> (def conn (redis/create-pool))
=> (redis/close-pool conn)
```

`0.2.x` and later:

```clojure
=> (def conn (redis/connect))
=> (redis/disconnect conn)
```

### configuration params

some renames, some addtions, some regroupping:

```clojure
{:nodes                                  ;; [{:host "1.1.1.1" :port 6379} {:host "2.2.2.2" :port 6380}]
 :to                                     ;; :cluster, :sentinel
 :connection-timeout socket-timeout
 :max-attempts
 :username password
 :database-index
 :ssl?
 :client-name
 :master-name
 :sentinel-client-config                 ;; if not provided a default config will be created if sentinel is used
 :pool-size pool-max-wait pool-max-idle}
```

### pipelines

they are currently on Jedis and will have to be ported to UnifiedJedis<br/>
in this version they won't work

# 0.1.4809

* add params to `set` ([what params](https://redis.io/commands/set/)?)

```clojure
=> (redis/set conn "foo:bar:checksum" "814e2a88-5acb-4fa1-9a2a-c4c7e82ee6e2"
                   {:ex 3})
"OK"

=> (redis/get conn "foo:bar:checksum")
"814e2a88-5acb-4fa1-9a2a-c4c7e82ee6e2"

;; 3 seconds later
=> (redis/get conn "foo:bar:checksum")
nil
```

# 0.1.4808

* add optional [max-idle](https://commons.apache.org/proper/commons-pool/apidocs/org/apache/commons/pool2/impl/GenericObjectPool.html#setMaxIdle-int-) to the pool for heavier loads

# 0.1.4807

* upgrade to Jedis 4.2.0
* switch back to JedisPool

# 0.1.4806

* switch to the core GenericObjectPoolConfig

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
