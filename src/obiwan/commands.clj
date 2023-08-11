(ns obiwan.commands
  (:refer-clojure :exclude [get set type keys])
  (:require [obiwan.tools :as t])
  (:import [redis.clients.jedis.params SetParams]))

;; wrap Java methods to make them composable

;; check whether creating a function every time in (op redis #(this)) affects performance / GC collections
;; only _iff_ it is of any significance precreate these
;;      no matter the solution the public redis functions should remain _composable_
;;      but.. pipelining commands should solve most if not all

;; these command functions are decoupled from redis connection to enable pipeling
;; i.e. to build command functions at runtime and then be passed into a pipeline

;; TODO: add an "options" map

;; hash

(defn hget [h f]
  #(.hget % h f))

(defn hset [h m]
  #(.hset % h m))

(defn hmget [redis h fs]
  (.hmget redis h (into-array String fs)))

(defn hmset [redis h m]
  (.hmset redis h m))

#_(defn hmget [h fs]
  #(.hmget % h (into-array String fs)))

#_(defn hmset [h m]
  #(.hmset % h m))

(defn hgetall [h]
  #(.hgetAll % h))

(defn hdel [h vs]
  #(.hdel % h (into-array String vs)))

;; sorted set

(defn zadd [k m]
  #(.zadd % k m))

(defn zrange [k zmin zmax]
  #(.zrange % k zmin zmax))

;; set

(defn smembers [s]
  #(.smembers % s))

(defn scard [s]
  #(.scard % s))

(defn sismember [s v]
  #(.sismember % s v))

(defn sadd [s vs]
  #(.sadd % s (into-array
                String vs)))

(defn srem [s vs]
  #(.srem % s (into-array
                String vs)))

;; basic operations

(defn ->set-params [{:keys [xx nx px ex exat pxat keepttl get]}]
  (cond-> (SetParams/setParams)
    xx (.xx)
    nx (.nx)
    px (.px px)
    ex (.ex ex)
    exat (.exAt exat)
    pxat (.pxAt pxat)
    keepttl (.keepttl)
    get (.get)))

(defn set
  ([k v]
   #(.set % k v))
  ([k v params]
   (let [ps (->set-params params)]
     #(.set % k v ps))))

(defn get [k]
  #(.get % k))

(defn mset [m]
  #(.mset % (t/m->array m)))

(defn mget [ks]
  #(.mget % (into-array ks)))

(defn del [ks]
  #(.del % (into-array ks)))

(defn exists [vs]
  #(.exists % (into-array vs)))

(defn type [k]
  #(.type % k))

(defn keys [k]
  #(.keys % k))

(defn incr [k]
  #(.incr % k))

(defn incr-by [k v]
  #(.incrBy % k v))

(defn decr [k]
  #(.decr % k))

(defn decr-by [k v]
  #(.decrBy % k v))

;; ops

(defn module-load [path]
  #(.moduleLoad % path))

(defn module-unload [mname]
  #(.moduleLoad % mname))
