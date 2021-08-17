(ns dev
  (:require [clj-http.client :as http]
            [jsonista.core :as json]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [spec :as rspec]))

(def spec (rspec/slurp-redis-spec))

(defn ns-fns [nspace]
 (let [raw (-> nspace symbol ns-publics)]
   (->> (for [[k v] raw]
          [(keyword k) v])
        (into {}))))

;;TODO: add as a hook on building a jar
(defn add-redis-docs [nspace]
  (doseq [[fname path] (ns-fns nspace)]
    (when-let [rdoc (fname spec)]
      (let [current (-> path meta :doc)
            doc (if current
                  {:obiwan-doc current
                   :redis-doc rdoc}
                  {:redis-doc rdoc})]
        (alter-meta! path
                     assoc :doc doc)))))
