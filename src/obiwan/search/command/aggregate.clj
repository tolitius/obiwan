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

(deftype Reduce [fun args as]
  cmd/Parameter
  (validate [param]
    (if (and (seq fun)
             (seq args))
      {:valid? true}
      {:valid? false
       :spec "need function and arguments"
       :what-i-see {:function fun :arguments args}}))
  (redisify [param]
    (->> (cond-> ["REDUCE" fun (count args)]
           (seq args) (conj args)
           (seq as)   (conj ["AS" as]))
         flatten
         (map str))))

(defn make-reducer [{:keys [fn fields as]}]
  (Reduce. fn fields as))

(deftype GroupBy [fields reducers]
  cmd/Parameter
  (validate [param]
    (if (and (seq fields)
             (or (empty? reducers)
                 (every? cmd/validate reducers)))
        {:valid? true}
        {:valid? false
         :spec "groupby should have fields and valid reducers"
         :what-i-see {:fields fields :reducers reducers}}))
  (redisify [param]
    (-> ["GROUPBY" (str (count fields)) (map str fields) (map cmd/redisify reducers)]
        flatten)))

(deftype GroupBys [group-bys]
  cmd/Parameter
  (validate [param]
    (mapv cmd/validate group-bys))
  (redisify [param]
    (mapcat cmd/redisify group-bys)))

(defn make-group-by [{:keys [by reduce]}]
  (GroupBy. by (mapv make-reducer reduce)))

(defn make-group-bys [groups]
  (cond
    (map? groups)        (make-group-by groups)
    (sequential? groups) (GroupBys. (mapv make-group-by groups))
    :else (throw         (RuntimeException. (str "\"group by\" can be either a map for single \"group by\""
                                                 " or a list/vector for many \"group by\"s")))))

(deftype Apply [expr as]
  cmd/Parameter
  (validate [param]
    (if (and (seq expr)
             (seq as))
        {:valid? true}
        {:valid? false
         :spec "\"expr\" and \"as\" can't be empty"
         :what-i-see {:expr expr :as as}}))
  (redisify [param]
    ["APPLY" expr "AS" as]))

(defn make-apply [{:keys [expr as]}]
  (Apply. expr as))

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
    ["LIMIT" (str offset) (str number)]))

(defn make-limit [{:keys [offset number]}]
  (Limit. offset number))

(defn response->human [xs]
  {:found (first xs)
   :results (mapv t/bytes->map (rest xs))})

(defn debug [x]
  (clojure.pprint/pprint x)
  x)

;; TODO: because APPLY and GROUPBY and others come in many
;;       plus can be positioned anywhere in a query addiing a different meaning that depends on a position
;;       the actual query has to be a vector of maps vs. a map
(defn aggregate-index [^JedisPool redis
                       iname
                       query
                       {:keys [group
                               apply
                               limit
                               ;; TODO: add other search options
                               ]}]
  (let [separator "-@@@-"    ;; TODO: needs a cleaner idea that would still keep not interfering with qeury strings
        params (cond-> {}
                 (seq apply)  (assoc :apply (make-apply apply))
                 (seq group)  (assoc :group-by (make-group-bys group))
                 (seq limit)  (assoc :limit (make-limit limit))
                 ;; TODO: add other definitions opts
                 )
        opts (->> params
                  vals
                  (mapcat cmd/redisify)
                  (cons query)
                  (cons iname)
                  debug
                  (into-array String))
        send-search #(-> (t/send-command cmd/FT_AGGREGATE opts %)
                         t/binary-multi-bulk-reply)]
    (-> (oc/op redis send-search)
        response->human)))
