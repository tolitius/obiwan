(ns obiwan.test.tools
  (:require [obiwan.core :as redis]
            [yang.lang :as y])
  (:import [redis.embedded RedisServer
                           RedisExecProvider]
           [redis.embedded.util OS]))

(defn make-redis-server [{:keys [path
                                 os
                                 architecture
                                 port
                                 modules] ;; path-to-server --loadmodule path-to/redisearch.so
                          :or {os "MAC_OS_X"}
                          :as opts}]
  (println "making redis server" opts)
  (let [provider (doto (RedisExecProvider/defaultProvider)
                       (.override (OS/MAC_OS_X/valueOf os)
                                  path))]

    (RedisServer. provider port)))

(defn start-redis-server
  ([]
   (start-redis-server {}))
  ([{:keys [config]
     :or {config "resources/config.edn"}}]
   (let [opts (-> (y/edn-resource config)
                  :redis
                  :server)
         server (make-redis-server opts)]
     (.start server)
     server)))

(defn stop-redis-server [server]
  (.stop server)
  :redis-server-stopped)

(defn make-connection-pool
  ([]
   (make-connection-pool {}))
  ([{:keys [config]
     :or {config "resources/config.edn"}}]
   (let [port (-> (y/edn-resource config)
                  :redis
                  :server
                  :port)]
         (redis/create-pool {:port port}))))

(defn with-redis [f]
  (let [server (start-redis-server)]
    (f)
    (stop-redis-server server)))

(def ^:dynamic conn nil)

(defn with-connection-pool [f]
  (binding [conn (make-connection-pool)]
    (f)
    (redis/close-pool conn)))

(defn with-flushall [f]
    (f)
    (redis/say conn "FLUSHALL"))
