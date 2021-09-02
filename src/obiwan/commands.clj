(ns obiwan.commands)

;; wrap Java methods to make them composable

;; check whether creating a function every time in (op redis #(this)) affects performance / GC collections
;; only _iff_ it is of any significance precreate these
;;      no matter the solution the public redis functions should remain _composable_
;;      but.. pipelining commands should solve most if not all

;; these command functions are decoupled from redis connection to enable pipeling
;; i.e. to build command functions at runtime and then be passed into a pipeline

;; TODO:
;;       * enable pipelining for all fns (sets, sorted sets, etc..)
;;       * add an options map

;; hash

(defn hget [h f]
  #(.hget % h f))

(defn hset [h m]
  #(.hset % h m))

(defn hmget [h fs]
  #(.hmget % h (into-array String fs)))

(defn hmset [h m]
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
