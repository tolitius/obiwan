(ns obiwan.search.command.core
  (:require [obiwan.core :as oc]
            [obiwan.tools :as t]))

(defonce FT_CREATE (t/make-protocol-command "FT.CREATE"))
(defonce FT_SEARCH (t/make-protocol-command "FT.SEARCH"))
(defonce FT__LIST (t/make-protocol-command "FT._LIST"))
(defonce FT_DROPINDEX (t/make-protocol-command "FT.DROPINDEX"))

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
