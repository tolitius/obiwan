(ns obiwan.core
  (:refer-clojure :exclude [get set type keys])
  (:require [obiwan.tools :as t])
  (:import [redis.clients.jedis Jedis
                                Protocol
                                JedisPool
                                JedisPoolConfig
                                ScanParams
                                ScanResult]
           [redis.clients.jedis.exceptions JedisConnectionException]
           [org.apache.commons.pool2.impl GenericObjectPool]))

(defn new-conn [^JedisPool pool]
  (.getResource pool))

(defn op [pool f]
  (with-open [^Jedis r (new-conn pool)]
    (f r)))

(defn create-pool
  ([]
   (create-pool {}))
  ([{:keys [host port pool timeout password]
     :or {host "127.0.0.1"
          port 6379
          timeout Protocol/DEFAULT_TIMEOUT
          pool {:size 42
                :max-wait 30000}}}]
   (let [conf (doto (JedisPoolConfig.)
                (.setMaxTotal (pool :size))
                (.setMaxWaitMillis (pool :max-wait)))]
     (println (str "connecting to Redis " host ":" port ", timeout: " timeout ", pool: " pool))
     (JedisPool. conf ^String host ^int port ^int timeout ^String password))))

(defn close-pool [pool]
  (println "disconnecting from Redis:" pool)
  (.destroy pool)
  :pool-closed)

(defn connected? [pool]
  (try
    (op pool (fn [_] true))
    (catch JedisConnectionException ex
      (println ex)
      false)))

;; TODO: waiting on 4.x Jedis branch to open up the BaseGenericObjectPool getters
(defn pool-stats [pool]
  {:active-resources (.getNumActive pool)
   ; :max-total (.getMaxTotal pool)
   ; :max-wait-ms (.getMaxWaitMillis pool)
   ; :created-count (.getCreatedCount pool)
   ; :returned-count (.getReturnedCount pool)
   :number-of-waiters (.getNumWaiters pool)
   :idle-resources (.getNumIdle pool)})

;; send arbitrary command to the server
(defn say
  ([redis what]
   (say redis what {}))
  ([redis what {:keys [args expect parse]
                 :or {expect t/status-code-reply
                      parse identity}}]
   (let [cmd (t/make-protocol-command what)
         jargs (when args ;; TODO: deal with byte[] args
                 (into-array String (if (sequential? args)
                                      args
                                      [args])))
         say-it #(-> (t/send-command cmd jargs %)
                     expect)]
         (->> say-it
              (op redis)
              parse))))

;; new, not yet Jedis supported commands

(defn hello [^JedisPool redis]
  (let [cmd (t/make-protocol-command "HELLO")
        say-hello #(-> (t/send-command cmd nil %)
                       t/binary-multi-bulk-reply)
        reply (->> say-hello
                   (op redis)
                   t/bytes->map)
        modules (mapv t/bytes->map
                      (clojure.core/get reply "modules"))] ;; TODO: later recursive bytes->type
    (assoc reply "modules" modules)))


;; wrap Java methods to make them composable

;; check whether creating a function every time in (op redis #(this)) affects performance / GC collections
;; only _iff_ it is of any significance precreate these
;;      no matter the solution the public redis functions should remain _composable_
;;      but.. pipelining commands should solve most if not all

;; the private redis command functions that are decoupled from redis connection enable pipeling
;; i.e. to build command functions at runtime and then be passed into a pipeline
;; TODO: enable pipelining for all fns (sets, sorted sets, etc..)

;; hash

(defn- -hget- [h f]
  #(.hget % h f))

(defn- -hset- [h m]
  #(.hset % h m))

(defn- -hmget- [h fs]
  #(.hmget % h (into-array String fs)))

(defn- -hmset- [h m]
  #(.hmset % h m))

(defn- -hgetall- [h]
  #(.hgetAll % h))

(defn- -hdel- [h vs]
  #(.hdel % h (into-array String vs)))

(defn ^{:doc {:obiwan-doc
              "takes in a jedis connection pool, a hash name and a field name if present, returns a field name value"}}
  hget
  ([h f] (-hget- h f))
  ([^JedisPool redis h f]
   (op redis (-hget- h f))))

(defn hset
  ([h m] (-hset- h m))
  ([redis h m]
   (op redis (-hset- h m))))

(defn hmget
  ([h fs] (-hmget- h fs))
  ([^JedisPool redis h fs]
   (into [] (op redis (-hmget- h fs)))))

(defn hmset
  ([h m] (-hmset- h m))
  ([redis h m]
   (op redis (-hmset- h m))))

(defn hgetall
  ([h] (-hgetall- h))
  ([^JedisPool redis h]
   (into {} (op redis (-hgetall- h)))))

(defn hdel
  ([h vs] (-hdel- h vs))
  ([redis h vs]
   (op redis (-hdel- h vs))))

;; sorted set

(defn zadd [redis s m]
  (op redis #(.zadd % s m)))

(defn zrange [redis s zmin zmax]
  (op redis #(.zrange % s zmin zmax)))

;; set

(defn smembers [redis s]
  (op redis #(.smembers % s)))

(defn scard [redis s]
  (op redis #(.scard % s)))

(defn sismember [redis s v]
  (op redis #(.sismember % s v)))

(defn sadd [redis s vs]
  (op redis #(.sadd % s (into-array
                          String vs))))

(defn srem [redis s vs]
  (op redis #(.srem % s (into-array
                         String vs))))

;; basic operations

(defn set [redis k v]
  (op redis #(.set % k v)))

(defn mset [redis kv]
  (op redis #(.mset % (into-array kv))))

(defn get [redis k]
  (op redis #(.get % k)))

(defn mget [redis ks]
  (op redis #(.mget % (into-array ks))))

(defn del [redis ks]
  (op redis #(.del % (into-array ks))))

(defn exists [redis vs]
  (op redis #(.exists % (into-array vs))))

(defn type [redis k]
  (op redis #(.type % k)))

(defn keys [redis k]
  (op redis #(.keys % k)))

(defn incr [redis k]
  (op redis #(.incr % k)))

(defn incr-by [redis k v]
  (op redis #(.incrBy % k v)))

(defn decr [redis k]
  (op redis #(.decr % k)))

(defn decr-by [redis k v]
  (op redis #(.decrBy % k v)))

;; pipeline

(defn make-pipeline [conn]
  (.pipelined conn))

(defn sync-pipeline [pipe]
  (.sync pipe))

(defn realize-responses [rs]
  (mapv #(.get %) rs))

(defn pipeline [redis commands]
  (op redis
      (fn [conn]
        (let [pipe (make-pipeline conn)
              rs (mapv #(% pipe)
                       commands)]
          (sync-pipeline pipe)
          (realize-responses rs)))))

;; scaning things

(defn new-scan-params [{:keys [fetch-size pattern]
                        :or {fetch-size 10}}]
  (doto (ScanParams.)
        (.count (int fetch-size))))

(defn sscan
  ([redis s cur]
   (sscan redis s cur {}))
  ([redis s cur params]
   (let [^ScanResult rs (op redis #(.sscan % s cur (new-scan-params params)))
         batch  (.getResult rs)
         cursor (.getCursor rs)]
     {:batch batch :cursor cursor :done? (.isCompleteIteration rs)})))


;; TODO: if/when needed generalize to scan, hscan, zscan
(defn scan-all
  "scan a whole set 's' and apply a function 'f' on each 'batch'"
  ([redis s f]
   (scan-all redis s f {}))
  ([redis s f params]
   (loop [cursor "0"
          rs (sscan redis s "0" params)]
     (let [{:keys [batch cursor done?]} rs]
       (f batch)
       (when-not done?
         (recur cursor (sscan redis s cursor params)))))))
