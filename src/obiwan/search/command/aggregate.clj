(ns obiwan.search.command.aggregate
  (:require [obiwan.core :as oc]
            [obiwan.search.command.core :as cmd]
            [obiwan.tools :as t])
  (:import [redis.clients.jedis JedisPool]))


;; (!) this command is work in progress


;; ------------------------
;; FT.AGGREGATE
;; ------------------------

;; FT.AGGREGATE {index_name} {query_string}
;;              [VERBATIM]
;;              [LOAD {nargs} {property} ...]
;;              [GROUPBY {nargs} {property} ...
;;                REDUCE {func} {nargs} {arg} ... [AS {name:string}]
;;                ...
;;              ] ...
;;              [SORTBY {nargs} {property} [ASC|DESC] ... [MAX {num}]]
;;              [APPLY {expr} AS {alias}] ...
;;              [LIMIT {offset} {num}] ...
;;              [FILTER {expr}] ...

(deftype Limit [offset number]
  cmd/Parameter
  (validate [param]
    (if (and (integer? offset)
             (integer? number))
        {:valid? true}
        {:valid? false
         :spec "offset and number should be integers"
         :what-i-see {:offset offset :number number}}))
  (redisify [param]
    (str "LIMIT " offset " " number)))

(defn make-limit [{:keys [offset number]}]
  (Limit. offset number))

(defn response->human [xs]
  {:found (first xs)
   :results (mapv
              (fn [[k v]]
                {k
                 (t/bytes->map v)})
              (partition 2 (rest xs)))})

(defn aggregate-index [^JedisPool redis
                       iname
                       query
                       {:keys [limit
                               ;; TODO: add other search options
                               ]}]
  (let [separator "-@@@-"    ;; TODO: needs a cleaner idea that would still keep not interfering with qeury strings
        params (cond-> {}
                 (seq limit) (assoc :limit (make-limit limit))
                 ;; TODO: add other definitions opts
                 )
        opts (-> [iname
                  query
                  (-> params vals cmd/redisify-params)]
                  (t/xs->str separator)
                  (t/tokenize separator)
                  (->> (into-array String)))
        send-search #(-> (t/send-command cmd/FT_AGGREGATE opts %)
                         t/binary-multi-bulk-reply)]
    (-> (oc/op redis send-search)
        response->human)))
