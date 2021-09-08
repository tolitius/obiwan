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

(defn ft-list [conn]
  (let [ask-for-list #(-> (t/send-command FT__LIST nil %)
                          t/binary-multi-bulk-reply)]
    (->> ask-for-list
         (oc/op conn)
         t/bytes->seq)))

(defn ft-drop-index [conn index-name dd?]
  (let [args (if dd?
               (into-array String [index-name "DD"])
               (into-array String [index-name]))
        drop-it #(-> (t/send-command FT_DROPINDEX args %)
                     t/status-code-reply)]
    (->> drop-it
         (oc/op conn))))

(defn ft-sugadd [conn k string score {:keys [incr? payload]}]
  (let [;;TODO: add arg validation
        args (->> (cond-> [k string (str score)]
                    incr?   (conj "INCR")
                    payload (conj "PAYLOAD" payload))
                  (into-array String))
        add-it #(-> (t/send-command FT_SUGADD args %)
                    t/integer-reply)]
    (->> add-it
         (oc/op conn))))

(defn ft-sugget [conn k prefix {:keys [fuzzy? with-scores? with-payloads? max]}]
  (let [;;TODO: add arg validation
        args (->> (cond-> [k prefix]
                    fuzzy?          (conj "FUZZY")
                    with-scores?    (conj "WITHSCORES")
                    with-payloads?  (conj "WITHPAYLOADS")
                    max             (conj "MAX" (str max)))
                  (into-array String))
        get-it #(-> (t/send-command FT_SUGGET args %)
                    t/multi-bulk-reply)
        sugs (->> get-it
                  (oc/op conn))]
    (cond
      (and with-payloads?
           with-scores?)  (->> sugs (partition 3) (mapv #(zipmap [:suggestion :score :payload] %)))
      with-payloads?      (->> sugs (partition 2) (mapv #(zipmap [:suggestion :payload] %)))
      with-scores?        (->> sugs (partition 2) (mapv #(zipmap [:suggestion :score] %)))
      :else               (->> sugs (mapv #(zipmap [:suggestion] [%]))))))

(defn ft-sugdel [conn k string]
  (let [;;TODO: add arg validation
        args (into-array String [k string])
        add-it #(-> (t/send-command FT_SUGDEL args %)
                    t/integer-reply)]
    (->> add-it
         (oc/op conn))))

(defn ft-suglen [conn k]
  (let [;;TODO: add arg validation
        args (into-array String [k])
        add-it #(-> (t/send-command FT_SUGLEN args %)
                    t/integer-reply)]
    (->> add-it
         (oc/op conn))))
