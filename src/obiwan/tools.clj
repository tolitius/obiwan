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
    client))

(defn status-code-reply [client]
  (.getStatusCodeReply client))

(defn multi-bulk-reply [client]
  (.getMultiBulkReply client))

(defn binary-multi-bulk-reply [client]
  (.getBinaryMultiBulkReply client))

(defn bytes->str [bs]
  (if (bytes? bs)
    (String. bs)
    bs))

(defn bytes->map [bss]
  (->> bss
       (map bytes->str)
       (apply hash-map)))

(defn fmv
  "apply f to each value v of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [k (f v)])))

(defn fmk
  "apply f to each key k of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [(f k) v])))

(defn value? [v]
  (or (number? v)
      (seq v)))
