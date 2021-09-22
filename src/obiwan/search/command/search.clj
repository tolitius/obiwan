(ns obiwan.search.command.search
  (:require [clojure.string :as s]
            [obiwan.core :as oc]
            [obiwan.search.command.core :as cmd]
            [obiwan.tools :as t])
  (:import [redis.clients.jedis JedisPool]))

;; ------------------------
;; FT.SEARCH
;; ------------------------

;; FT.SEARCH {index} {query}
;;           [NOCONTENT]
;;           [VERBATIM]
;;           [NOSTOPWORDS]
;;           [WITHSCORES]
;;           [WITHPAYLOADS]
;;           [WITHSORTKEYS]
;;           [FILTER {numeric_field} {min} {max}] ...
;;           [GEOFILTER {geo_field} {lon} {lat} {radius} m|km|mi|ft]
;;           [INKEYS {num} {key} ... ]
;;           [INFIELDS {num} {field} ... ]
;;           [RETURN {num} {field} ... ]
;;           [SUMMARIZE [FIELDS {num} {field} ... ]
;;                      [FRAGS {num}]
;;                      [LEN {fragsize}]
;;                      [SEPARATOR {separator}]]
;;           [HIGHLIGHT [FIELDS {num} {field} ... ]
;;                      [TAGS {open} {close}]]
;;           [SLOP {slop}]
;;           [INORDER]
;;           [LANGUAGE {language}]
;;           [EXPANDER {expander}]
;;           [SCORER {scorer}]
;;           [EXPLAINSCORE]
;;           [PAYLOAD {payload}]
;;           [SORTBY {field} [ASC|DESC]]
;;           [LIMIT offset num]

(deftype Sort [by order]
  cmd/Parameter
  (validate [param]
    (if (and (seq by)
             (#{:asc :desc} order))
      {:valid? true}
      {:valid? false
       :spec "sort needs a {prop order} map, where order needs to be :asc or :desc"
       :what-i-see {:args by}}))
  (redisify [param]
    ["SORTBY" by (-> order name s/upper-case)]))

(defn make-sort [{:keys [by]}]
  (let [[field order] (first by)]
    (Sort. field order)))

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
                {(String. k)
                 (t/bytes->map v)})
              (partition 2 (rest xs)))})

(defn opt->command [opt]
  (if-not (map? opt)
    (throw (RuntimeException. (str "invalid search option. search options should be a vector of maps with keys"
                                   " matching FT.SEARCH spec. for example: "
                                   "[{:sort {:by {\"field-name\" :desc}}} {:limit {:number 0 :offset 42}}]")))
    (let [[oname args] (first opt)]
      (case oname
        :sort  (make-sort args)
        :limit (make-limit args)
        (throw (RuntimeException. (str "'" oname
                                       "' search option is not (yet?) implemented for " opt)))))))

(defn search-index [^JedisPool redis
                     iname
                     query
                     opts]
  (let [params (mapv opt->command opts)
        opts (->> params
                  (mapcat cmd/redisify)
                  (cons query)
                  (cons iname)
                  ; debug
                  (into-array String))
        send-search #(-> (t/send-command cmd/FT_SEARCH opts %)
                         t/binary-multi-bulk-reply)]
    (-> (oc/op redis send-search)
        response->human)))
