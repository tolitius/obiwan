(ns obiwan.core
  (:refer-clojure :exclude [get set type keys])
  (:require [obiwan.commands :as c]
            [obiwan.tools :as t])
  (:import [redis.clients.jedis Protocol
                                Pipeline
                                HostAndPort
                                UnifiedJedis
                                JedisCluster
                                JedisSentineled
                                JedisSentinelPool
                                DefaultJedisClientConfig
                                JedisPool
                                JedisPooled
                                JedisPoolConfig]
           [redis.clients.jedis.util Pool]
           [redis.clients.jedis.params ScanParams]
           [redis.clients.jedis.resps ScanResult]
           [redis.clients.jedis.exceptions JedisConnectionException]
           [java.time Duration]
           [org.apache.commons.pool2.impl GenericObjectPool GenericObjectPoolConfig]))

(defn connected-as [client]
  (condp instance? client
    JedisPool         :jedis-pool
    JedisPooled       :jedis-pooled
    JedisSentineled   :jedis-sentineled
    JedisSentinelPool :jedis-sentinel-pool
    JedisCluster      :jedis-cluster
    :unknown-client-type))

(defn make-host-and-ports
  "convert [{:host \"1.1.1.1\" :port 6379}
            {:host \"2.2.2.2\" :port 6380}
            ..]
   to a set of HostAndPort instances"
  [nodes]
  (->> nodes
       (map (fn [{:keys [host port]}]
              (HostAndPort. host port)))
       (into #{})))

(defn make-client-config [{:keys [username
                                  password
                                  database-index
                                  connection-timeout
                                  socket-timeout
                                  ssl?
                                  client-name]
                           :or {connection-timeout Protocol/DEFAULT_TIMEOUT
                                socket-timeout Protocol/DEFAULT_TIMEOUT
                                database-index Protocol/DEFAULT_DATABASE
                                ssl? false
                                client-name "kenobi"}}]
  (-> (DefaultJedisClientConfig/builder)
      (.user username)
      (.password password)
      (.database database-index)
      (.connectionTimeoutMillis connection-timeout)
      (.socketTimeoutMillis socket-timeout)
      (.ssl ssl?)
      (.clientName client-name)
      .build))

(defn make-sentineled [pool-config
                       master-client-config
                       sentinels
                       {:keys [master-name
                               sentinel-client-config]
                        :as opts}]

  (when-not (seq master-name)
    (throw (RuntimeException. "can't create a sentineled redis client without a master name. please provide one via a :master-name param")))

  (let [sentinel-config (or sentinel-client-config
                            ;; make a default sentinel client config is not passed in
                            (make-client-config (assoc opts
                                                       :client-name "sentinel-kanobi")))]
    (JedisSentineled. master-name
                      master-client-config
                      pool-config
                      sentinels
                      sentinel-config)))

(defn connect
  ([]
   (connect {}))
  ([{:keys [nodes                                  ;; [{:host "1.1.1.1" :port 6379} {:host "2.2.2.2" :port 6380}]
            to                                     ;; :cluster, :sentinel
            connection-timeout socket-timeout
            max-attempts
            username password
            database-index
            ssl?
            client-name
            master-name
            sentinel-client-config                 ;; if not provided a default config will be created if sentinel is used
            pool-size pool-max-wait pool-max-idle]
     :or {nodes [{:host "127.0.0.1" :port 6379}]
          to :default
          max-attempts JedisCluster/DEFAULT_MAX_ATTEMPTS
          pool-size 42
          pool-max-wait 30000
          pool-max-idle 8}
     :as opts}]
   (let [pool-config      (doto (JedisPoolConfig.)
                            (.setMaxTotal pool-size)
                            (.setMaxIdle pool-max-idle)
                            (.setMaxWaitMillis pool-max-wait))
         client-config    (make-client-config opts)
         hosts-and-ports  (make-host-and-ports nodes)]
     (case to
       :default (do (println (str "connecting to Redis " nodes ", config: "
                                  (dissoc opts :username :password :to :nodes)))
                    (JedisPooled. (first hosts-and-ports)
                                  client-config
                                  pool-config))
       :cluster (do (println (str "connecting to Redis cluster " nodes ", config: "
                                  (dissoc opts :username :password :to :nodes)))
                    (JedisCluster. hosts-and-ports
                                   client-config
                                   max-attempts
                                   pool-config))
       :sentinel (do (println (str "connecting to Redis sentineled " nodes ", config: "
                                   (dissoc opts :username :password :to :nodes)))
                     (make-sentineled pool-config
                                      client-config
                                      hosts-and-ports
                                      opts))
       (throw (RuntimeException.
                (str "\"" to "\" is an unknown source to connect to. supported are \":default\" and \":cluster\"")))))))

(defn disconnect [redis]
  (println "disconnecting from Redis")
  (case (connected-as redis)
    :jedis-pooled      (-> redis
                           .getPool
                           .destroy)
    :jedis-sentineled  :tbd
    :jedis-cluster     :tbd)
  :disconnected-from-redis)

(declare say)

(defn connected? [redis]
  (try
    (= "PONG"
       (say redis "PING" {:parse t/bytes->str}))
    (catch JedisConnectionException ex
      (println ex)
      false)))

;; TODO: waiting on 4.x Jedis branch to open up the BaseGenericObjectPool getters
(defn pool-stats [redis]
  (if (= (connected-as redis)
         :jedis-pooled)
    (let [pool (.getPool redis)]
      {:active-resources (.getNumActive pool)
       ; :max-total (.getMaxTotal pool)
       ; :max-wait-ms (.getMaxWaitMillis pool)
       ; :created-count (.getCreatedCount pool)
       ; :returned-count (.getReturnedCount pool)
       :number-of-waiters (.getNumWaiters pool)
       :idle-resources (.getNumIdle pool)})
    {:pool-stats (str "not supported for "
                      (connected-as redis))}))

;; send arbitrary command to the server
(defn say
  ([redis what]
   (say redis what {}))
  ([redis what {:keys [args expect parse]
                :or {parse identity}}]
   (let [cmd (t/make-protocol-command what)
         jargs (when args                                  ;; TODO: deal with byte[] args
                 (into-array String (if (sequential? args)
                                      args
                                      [args])))]
     (-> redis
         (t/send-command cmd jargs)
         parse))))

;; new, not yet Jedis supported commands

(defn hello [redis]
  (let [cmd (t/make-protocol-command "HELLO")
        reply (-> (t/send-command redis cmd nil)
                  t/bytes->map)
        modules (mapv t/bytes->map
                      (clojure.core/get reply "modules"))] ;; TODO: later recursive bytes->type
    (assoc reply "modules" modules)))

;; TODO: add comman options (most likely via a function that would add options)
;;       to preserve the "direct" and "pipelining" interfaces
;;
;;       add pipelining to basic commands

;; hash

(defn ^{:doc {:obiwan-doc
              "takes in a jedis connection pool, a hash name and a field name if present, returns a field name value"}}
  hget
  ([^UnifiedJedis redis h f]
   (.hget redis h f)))

(defn hset [^UnifiedJedis redis h m]
   (.hset redis h m))

(defn hmget [^UnifiedJedis redis h fs]
  (->> (.hmget redis
               h
               (into-array String fs))
       (into [])))

(defn hgetall [^UnifiedJedis redis h]
  (->> (.hgetAll redis h)
       (into {})))

(defn hdel [^UnifiedJedis redis h vs]
  (.hdel redis h (into-array String vs)))

;; sorted set

(defn zadd [^UnifiedJedis redis k m]
  (.zadd redis k m))

(defn zrange [^UnifiedJedis redis k zmin zmax]
  (.zrange redis k zmin zmax))

;; set

(defn smembers [^UnifiedJedis redis s]
  (.smembers redis s))

(defn scard [^UnifiedJedis redis s]
  (.scard redis s))

(defn sismember [^UnifiedJedis redis s v]
  (.sismember redis s v))

(defn sadd [^UnifiedJedis redis s vs]
  (.sadd redis s (into-array
                   String vs)))

(defn srem [^UnifiedJedis redis s vs]
  (.srem redis s (into-array
                   String vs)))

;; basic operations

(defn set
  ([^UnifiedJedis redis k v]
   (.set redis k v))
  ([^UnifiedJedis redis k v params]
   (let [ps (c/->set-params params)]
     (.set redis k v ps))))

(defn get [^UnifiedJedis redis k]
  (.get redis k))

(defn mset [^UnifiedJedis redis m]
  (.mset redis (t/m->array m)))

(defn mget [^UnifiedJedis redis ks]
  (.mget redis (into-array ks)))

(defn del [^UnifiedJedis redis ks]
  (.del redis (into-array ks)))

(defn exists [^UnifiedJedis redis vs]
  (.exists redis (into-array vs)))

(defn type [^UnifiedJedis redis k]
  (.type redis k))

(defn keys [^UnifiedJedis redis k]
  (.keys redis k))

(defn incr [^UnifiedJedis redis k]
  (.incr redis k))

(defn incr-by [^UnifiedJedis redis k v]
  (.incrBy redis k v))

(defn decr [^UnifiedJedis redis k]
  (.decr redis k))

(defn decr-by [^UnifiedJedis redis k v]
  (.decrBy redis k v))

;; ops

(defn module-load [^UnifiedJedis redis path]
  (t/send-command redis
                  c/MODULE
                  (into-array String ["LOAD" path])))

(defn module-unload [^UnifiedJedis redis mname]
  (t/send-command redis
                  c/MODULE
                  (into-array String ["UNLOAD" mname])))

;; pipeline

(defn make-pipeline [^UnifiedJedis redis]
  (cond
    (instance? JedisPooled redis) (let [conn (-> redis
                                                 .getPool
                                                 .getResource)]
                                    {:conn conn :pipe (Pipeline. conn)})
    (instance? JedisCluster redis) (throw (RuntimeException.
                                            "redis pipeline is not yet supported on the type JedisCluster"))
    :else (throw (RuntimeException.
                   (str "redis pipeline is not supported on the type \"" (class redis) "\"")))))

(defn sync-pipeline [pipe]
  (.sync pipe))

(defn realize-responses [rs]
  (mapv #(.get %) rs))

(defn pipeline [^UnifiedJedis redis commands]
  (let [{:keys [conn pipe]} (make-pipeline redis)]
    (try (let [rs (map #(% pipe)
                       commands)]
           (sync-pipeline pipe)
           (realize-responses rs))
         (finally (.close conn)))))

;; scaning things

(defn new-scan-params [{:keys [fetch-size pattern]
                        :or {fetch-size 10}}]
  (doto (ScanParams.)
        (.count (int fetch-size))))

(defn sscan
  ([redis s cur]
   (sscan redis s cur {}))
  ([redis s cur params]
   (let [^ScanResult rs (.sscan redis s cur (new-scan-params params))
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
