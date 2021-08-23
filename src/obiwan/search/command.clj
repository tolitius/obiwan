(ns obiwan.search.command)

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

(defprotocol Parameter
  (validate [param])
  (redisify [param]))

(deftype CreatePrefix
  Redisify
  ())
