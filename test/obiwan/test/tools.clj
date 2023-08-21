(ns obiwan.test.tools
  (:require [obiwan.core :as redis]
            [obiwan.tools :as t]
            [clojure.java.io :as io]
            [yang.lang :as y])
  (:import [redis.embedded RedisServer
                           RedisServerBuilder
                           RedisExecProvider]
           [redis.embedded.util OS]))

(defn make-redis-server [{:keys [path
                                 os
                                 architecture
                                 port
                                 dir           ;; TODO: convert to a settings map
                                 modules]      ;; path-to-server --loadmodule path-to/redisearch.so
                          :or {os "MAC_OS_X"
                               dir "/tmp"}
                          :as opts}]
  (println "making redis server" opts)
  (let [provider (doto (RedisExecProvider/defaultProvider)
                   (.override (OS/MAC_OS_X/valueOf os)
                              path))
        rdbfile (str dir "/dump.rdb")]
    (io/delete-file rdbfile true)
    (-> (RedisServerBuilder.)
        (.redisExecProvider provider)
        (.port port)
        (.setting (str "dir " dir))
        (.build))))

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
         (redis/connect {:nodes [{:host "localhost"
                                  :port port}]}))))

(defn with-redis [f]
  (let [server (start-redis-server)]
    (f)
    (stop-redis-server server)))

(def ^:dynamic conn nil)

(defn with-connection-pool [f]
  (binding [conn (make-connection-pool)]
    (f)
    (redis/disconnect conn)))

(defn with-flushall [f]
    (f)
    (redis/say conn "FLUSHALL"))

(defn load-module
  ([conn mname]
   (load-module conn mname {}))
  ([conn
    mname
    {:keys [config]
     :or {config "resources/config.edn"}}]
   (let [module (-> (y/edn-resource config)
                    :redis
                    :server
                    :modules
                    mname)]
     (println "loading module" {mname module})
     (redis/module-load conn module))))

(defn with-search-module [f]
  (load-module conn :search)
  (f)
  ;; (redis/module-unload conn "search")  ;; can't unload since the module introduces a new data structure
  )
