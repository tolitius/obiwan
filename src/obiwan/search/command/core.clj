(ns obiwan.search.command.core)

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
       (apply str)))

