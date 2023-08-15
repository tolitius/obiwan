(ns obiwan.search.command.core
  (:require [obiwan.core :as oc]
            [obiwan.tools :as t]))

(defonce FT_CREATE (t/make-protocol-command "FT.CREATE"))
(defonce FT_SEARCH (t/make-protocol-command "FT.SEARCH"))
(defonce FT_AGGREGATE (t/make-protocol-command "FT.AGGREGATE"))
(defonce FT__LIST (t/make-protocol-command "FT._LIST"))
(defonce FT_DROPINDEX (t/make-protocol-command "FT.DROPINDEX"))
(defonce FT_SUGADD (t/make-protocol-command "FT.SUGADD"))
(defonce FT_SUGGET (t/make-protocol-command "FT.SUGGET"))
(defonce FT_SUGDEL (t/make-protocol-command "FT.SUGDEL"))
(defonce FT_SUGLEN (t/make-protocol-command "FT.SUGLEN"))

(defprotocol Parameter
  (validate [param])
  (redisify [param]))

(defn validate-params [params]
  (let [vs (mapv validate params)]
    (if (->> vs (any? :valid?) (every? true?))
      {:valid? true}
      {:valid? false
       :spec (filter :spec vs)})))

(defn redisify-params [params]
  (->> params
       (mapv redisify)
       t/xs->str))

;;

(defn ft-list [redis]
  (-> (t/send-command redis FT__LIST nil)
      t/bytes->seq))

(defn ft-drop-index [redis index-name dd?]
  (let [args (if dd?
               (into-array String [index-name "DD"])
               (into-array String [index-name]))]
    (-> (t/send-command redis FT_DROPINDEX args)
        t/bytes->str)))

(defn ft-sugadd [redis k string score {:keys [incr? payload]}]
  (let [;;TODO: add arg validation
        args (->> (cond-> [k string (str score)]
                    incr?   (conj "INCR")
                    payload (conj "PAYLOAD" payload))
                  (into-array String))]
    (t/send-command redis FT_SUGADD args)))

(defn ft-sugget [redis k prefix {:keys [fuzzy? with-scores? with-payloads? max]}]
  (let [;;TODO: add arg validation
        args (->> (cond-> [k prefix]
                    fuzzy?          (conj "FUZZY")
                    with-scores?    (conj "WITHSCORES")
                    with-payloads?  (conj "WITHPAYLOADS")
                    max             (conj "MAX" (str max)))
                  (into-array String))
        sugs (-> (t/send-command redis FT_SUGGET args)
                 t/bytes->seq)]
    (cond
      (and with-payloads?
           with-scores?)  (->> sugs (partition 3) (mapv #(zipmap [:suggestion :score :payload] %)))
      with-payloads?      (->> sugs (partition 2) (mapv #(zipmap [:suggestion :payload] %)))
      with-scores?        (->> sugs (partition 2) (mapv #(zipmap [:suggestion :score] %)))
      :else               (->> sugs (mapv #(zipmap [:suggestion] [%]))))))

(defn ft-sugdel [redis k string]
  (let [;;TODO: add arg validation
        args (into-array String [k string])]
    (t/send-command redis FT_SUGDEL args)))

(defn ft-suglen [redis k]
  (let [;;TODO: add arg validation
        args (into-array String [k])]
    (t/send-command redis FT_SUGLEN args)))
