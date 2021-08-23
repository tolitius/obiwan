(ns obiwan.search.command.create
  (:require [obiwan.search.command.core :as cmd]
            [obiwan.tools :as t]))

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
    (str "PREFIX " (count prefixes) " " (apply str prefixes))))

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

(defn make-text-field [{:keys [name no-stem? weight phonetic sortable? no-index?]}]
  (Field. name "TEXT" sortable? no-index?)) ;; TODO: add \"text\" opts support (no-stem?, weight, phonetic)

(defn add-field
  "polymorphism as is.."
  [{:keys [type] :as field}]
  (case type
    :text    (make-text-field field)
    ; :numeric
    ; :geo
    ; :tag
    (throw (RuntimeException. (str "can't add a field "
                                   field
                                   "to the schema due to invalid field type [" type "]. "
                                   "valid types are #{:text, :at glancenumeric, :geo, :tag}")))))

(defn create-index [iname {:keys [on
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
  (let [index-definition (cond-> {:index iname}
                           (seq prefix) (assoc :prefix (make-prefix prefix)))
        fields (mapv add-field schema)]
    {:definition index-definition
     :schema fields}))
