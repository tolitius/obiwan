(ns obiwan.search.command.create
  (:require [obiwan.core :as oc]
            [obiwan.search.command.core :as cmd]
            [obiwan.tools :as t])
  (:import [redis.clients.jedis JedisPool]))

;; ------------------------
;; FT.CREATE
;; ------------------------

;; FT.CREATE {index}
;;           [ON {structure}]
;;           [PREFIX {count} {prefix} [{prefix} ..]
;;           [FILTER {filter}]
;;           [LANGUAGE {default_lang}]
;;           [LANGUAGE_FIELD {lang_field}]
;;           [SCORE {default_score}]
;;           [SCORE_FIELD {score_field}]
;;           [PAYLOAD_FIELD {payload_field}]
;;           [MAXTEXTFIELDS]
;;           [TEMPORARY {seconds}]
;;           [NOOFFSETS]
;;           [NOHL]
;;           [NOFIELDS]
;;           [NOFREQS]
;;           [SKIPINITIALSCAN]
;;           [STOPWORDS {num} {stopword} ...]
;;           SCHEMA {field} [TEXT [NOSTEM]
;;                                [WEIGHT {weight}]
;;                                [PHONETIC {matcher}] |
;;                           NUMERIC                   |
;;                           GEO                       |
;;                           TAG [SEPARATOR {sep}]]
;;                          [SORTABLE]
;;                          [NOINDEX] ...

(deftype Prefix [prefixes]
  cmd/Parameter
  (validate [param]
    (if (t/value? prefixes)
        {:valid? true}
        {:valid? false
         :spec "if prefix is used it should not be empty"}))
  (redisify [param]
    (str "PREFIX " (count prefixes) " " (t/xs->str prefixes))))

(deftype Field [fname ftype sortable? no-index?]
  cmd/Parameter
  (validate [param]
    (let [types #{"TEXT" "NUMERIC" "GEO" "TAG"}]
      (if (and (t/value? fname)
               (t/value? ftype)
               (types ftype))
        {:valid? true}
        {:valid? false
         :spec (str "field name/type can not be empty, "
                    "field type should be on of these: " types)
         :what-i-see {:name fname :type ftype}})))
  (redisify [param]
    (cond-> (str fname " " ftype)
      sortable? (str " SORTABLE")
      no-index? (str " NOINDEX"))))

(deftype Schema [fields]
  cmd/Parameter
  (validate [param]
    (cmd/validate-params fields))
  (redisify [param]
    (str "SCHEMA " (cmd/redisify fields))))

(defn make-prefix [ps]
  (Prefix. ps))

(defn make-text-field [{:keys [name no-stem? weight phonetic sortable? no-index?] :as f}]
  (Field. name "TEXT" sortable? no-index?)) ;; TODO: add \"text\" opts support (no-stem?, weight, phonetic)

(defn make-numeric-field [{:keys [name sortable? no-index?]}]
  (Field. name "NUMERIC" sortable? no-index?))

(defn make-geo-field [{:keys [name sortable? no-index?]}]
  (Field. name "GEO" sortable? no-index?))

(defn make-tag-field [{:keys [name separator sortable? no-index?]}]
  (Field. name "TAG" sortable? no-index?)) ;; TODO: add \"tag\" opts support (separator)

(defn add-field [field]
  (let [maker (case (t/map-ns field)
                #{"text"}      make-text-field
                #{"numeric"}   make-numeric-field
                #{"geo"}       make-geo-field
                #{"tag"}       make-tag-field
                (throw (RuntimeException. (str "can't add a field "
                                               field
                                               " to the schema due to invalid field type " (t/map-ns field) ". "
                                               "valid types are " #{"text" "numeric" "geo" "tag"} ". "
                                               "example: " #:text{:name "foo", :sortable? true}))))]
    (-> field t/remove-key-ns (t/fmk keyword) maker)))

(defn create-index [^JedisPool redis
                    iname
                    {:keys [on
                            prefix
                            filter
                            language
                            language-field
                            score
                            score-field
                            payload-field
                            max-text-fields?
                            temporary
                            no-offsets?
                            no-hl?
                            no-fields?
                            no-freqs?
                            skip-initial-scan?
                            stop-words
                            schema]}]
  (let [index-definition (cond-> {}
                           (seq prefix) (assoc :prefix (make-prefix prefix))
                           ;; TODO: add other definitions opts
                           )
        fields (mapv add-field schema)
        opts (->> [iname
                   (-> index-definition vals cmd/redisify-params)
                   "SCHEMA"
                   (cmd/redisify-params fields)]
                  t/xs->str
                  t/tokenize
                  (into-array String))
        send-create #(-> (t/send-command cmd/FT_CREATE opts %)
                         t/status-code-reply)]
    (oc/op redis send-create)))
