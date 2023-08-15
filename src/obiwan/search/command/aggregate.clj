(ns obiwan.search.command.aggregate
  (:require [clojure.string :as s]
            [obiwan.core :as oc]
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

(defn make-group-by [{:keys [by reduce]}]
  (GroupBy. by (mapv make-reducer reduce)))

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

(deftype Sort [props smax]
  cmd/Parameter
  (validate [param]
    (if (and (map? props)
             (or (not smax)
                 (number? smax)))
      {:valid? true}
      {:valid? false
       :spec "sort needs a {prop order} map and an optional number \"max\" param"
       :what-i-see {:props props :max smax}}))
  (redisify [param]
    (let [pvec (-> (for [[k v] props]
                     [k
                      (-> v name s/upper-case)])
                   flatten)]
      (->> (cond-> ["SORTBY" (count pvec) pvec]
             smax (conj ["MAX" smax]))
           flatten
           (map str)))))

(defn make-sort [{:keys [by max]}]
  (Sort. by max))

(defn response->human [xs]
  {:found (first xs)
   :results (mapv t/bytes->map (rest xs))})

(defn opt->command [opt]
  (if-not (map? opt)
    (throw (RuntimeException. (str "invalid aggregate option. aggregate options should be a vector of maps with keys"
                                   " matching FT.AGGREGATE spec. for example: "
                                   "[{:group {:by [\"@field-name\"] :reduce [{...}]}} {:limit {:number 0 :offset 42}}]")))
    (let [[oname args] (first opt)]
      (case oname
        :group (make-group-by args)
        :sort  (make-sort args)
        :apply (make-apply args)
        :limit (make-limit args)
        (throw (RuntimeException. (str "'" oname
                                       "' aggregate option is not (yet?) implemented for " opt)))))))

(defn debug [x]
  (clojure.pprint/pprint x)
  x)

;; TODO: because APPLY and GROUPBY and others come in many
;;       plus can be positioned anywhere in a query addiing a different meaning that depends on a position
;;       the actual query has to be a vector of maps vs. a map
(defn aggregate-index [^JedisPool redis
                                  iname
                                  query
                                  opts]
  (let [params (mapv opt->command opts)
        opts (->> params
                  (mapcat cmd/redisify)
                  (cons query)
                  (cons iname)
                  ; debug
                  (into-array String))]
    (-> (t/send-command redis cmd/FT_AGGREGATE opts)
        t/bytes->seq
        response->human)))
