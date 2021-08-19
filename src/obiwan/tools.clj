(ns obiwan.tools
  (:import [redis.clients.jedis BinaryJedis]
           [redis.clients.jedis.util SafeEncoder]
           [redis.clients.jedis.commands ProtocolCommand]))

(defn super-private-method
  "e.g. (super-private-method BinaryJedis
                              conn
                              \"checkIsInMultiOrPipeline\")
   where 'conn' is Jedis, a child of BinaryJedis"
  [super-class obj method & args]
  (let [m (->> (.getDeclaredMethods super-class)
               (filter #(.. % getName (equals method)))
               first)]
    (. m (setAccessible true))
    (. m (invoke obj args))))

(defn check-is-in-multi-or-pipeline [conn]
  (super-private-method BinaryJedis
                        conn
                        "checkIsInMultiOrPipeline"))

(defn make-protocol-command [cname]
  (reify ProtocolCommand
    (getRaw [this]
      (SafeEncoder/encode cname))))

(defn send-command [cmd args conn]
  (let [client (.getClient conn)]
    (check-is-in-multi-or-pipeline conn)
    (if args
      (.sendCommand client cmd args)
      (.sendCommand client cmd))
    (.getBinaryMultiBulkReply client)))  ;;TODO: take in the reply flavor

(defn str-reply [xs]
  (mapv #(if (bytes? %)
           (String. %)
           %)
        xs))
