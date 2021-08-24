(ns obiwan.search.command.search
  (:require [obiwan.core :as oc]
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
  (let [found (first xs)]
    (if (zero? found)
      found
      {:found found
       :results (mapv
                  (fn [[k v]]
                    {(String. k)
                     (t/bytes->map v)})
                  (partition 2 (rest xs)))})))

(defn search-index [^JedisPool redis
                     iname
                     query
                     {:keys [limit
                             ;; TODO: add other search options
                             ]}]
  (let [params (cond-> {}
                 (seq limit) (assoc :limit (make-limit limit))
                 ;; TODO: add other definitions opts
                 )
        opts (->> [iname
                   query
                   (-> params vals cmd/redisify-params)]
                  t/xs->str
                  t/tokenize
                  (into-array String))
        send-search #(-> (t/send-command cmd/FT_SEARCH opts %)
                         t/binary-multi-bulk-reply)]
    (-> (oc/op redis send-search)
        response->human)))
