(ns obiwan.tools
  (:require [clojure.string :as s])
  (:import [redis.clients.jedis Jedis UnifiedJedis JedisPooled Connection]
           [redis.clients.jedis.util SafeEncoder]
           [redis.clients.jedis.commands ProtocolCommand]))

(defn super-public-method
  "e.g. (super-public-method Connection
                             client
                             {:mname \"sendCommand\"
                              :mparams \"interface redis.clients.jedis.commands.ProtocolCommand\"
                             ...)
   where 'client' is redis.clients.jedis.Client, a grand child of Connection"
  [super-class obj {:keys [mname mparams]} & args]
  (let [m (->> (.getDeclaredMethods super-class)
               (filterv #(and (= mname   (.getName %))
                              (= mparams (apply str (.getParameterTypes %)))))
               first)]
    #_(println "calling " m ", with params:" mparams ", and args:" args)
    (if (seq args)
      (. m (invoke obj (into-array Object args)))
      (. m (invoke obj (into-array Object []))))))

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
  (super-private-method Jedis
                        conn
                        "checkIsInMultiOrPipeline"))

(defn make-protocol-command [cname]
  (reify ProtocolCommand
    (getRaw [this]
      (SafeEncoder/encode cname))))

(defn send-command [conn cmd args]
  ;; (check-is-in-multi-or-pipeline conn)
  (if args
    (super-public-method UnifiedJedis
                         conn
                         {:mname "sendCommand"
                          ; :mparams "interface redis.clients.jedis.commands.ProtocolCommandclass [[B"}
                          :mparams "interface redis.clients.jedis.commands.ProtocolCommandclass [Ljava.lang.String;"}
                         cmd
                         args)
    (super-public-method UnifiedJedis
                         conn
                         {:mname "sendCommand"
                          :mparams "interface redis.clients.jedis.commands.ProtocolCommand"}
                         cmd)))

(defn integer-reply [client]
  (.getIntegerReply client))

(defn status-code-reply [client]
  (.getStatusCodeReply client))

(defn multi-bulk-reply [client]
  (.getMultiBulkReply client))

(defn binary-multi-bulk-reply [client]
  (.getBinaryMultiBulkReply client))

(defn object-multi-bulk-reply [client]
  (.getObjectMultiBulkReply client))

(defn raw-object-multi-bulk-reply [client]
  (.getRawObjectMultiBulkReply client))

(defn unflushed-object-multi-bulk-reply [client]
  (.getUnflushedObjectMultiBulkReply client))

(defn bytes->str [bs]
  (if (bytes? bs)
    (String. bs)
    bs))

(defn bytes->seq [bss]
  (->> bss
       (map bytes->str)
       (apply vector)))

(defn bytes->map [bss]
  (->> bss
       (map bytes->str)
       (apply hash-map)))

(defn m->array [m]
  (->> m
       (mapcat identity)
       (into-array String)))

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

(defn map-ns [m]
  (->> m keys (map namespace) set))

(defn remove-key-ns
  ([m]
   (fmk m name))
  ([m kns]
  (->> (filter (fn [[k v]] (not= (namespace k)
                                 (name kns))) m)
       (into {}))))

(defn value? [v]
  (or (number? v)
      (seq v)))

(defn xs->str
  ([xs]
   (xs->str xs " "))
  ([xs separator]
   (->> xs
        (interpose separator)
        (apply str))))

(defn tokenize
  ([xs]
   (tokenize xs " "))
  ([xs separator]
   (s/split xs (re-pattern separator))))
