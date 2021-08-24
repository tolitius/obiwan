(ns obiwan.search.core
  (:require [obiwan.core :as oc]
            [obiwan.tools :as t]
            [obiwan.search.command.core :as cmd]
            [obiwan.search.command.create :as cc]
            [obiwan.search.command.search :as cs]))

(defn ft-create [conn index-name opts]
  (cc/create-index conn index-name opts))

(defn ft-search
  ([conn index-name query]
   (ft-search conn index-name query {}))
  ([conn index-name query opts]
   (cs/search-index conn index-name query opts)))

(defn ft-list [conn]
  (cmd/ft-list conn))

(defn ft-drop-index
  ([conn index-name]
   (cmd/ft-drop-index conn index-name false))
  ([conn index-name {:keys [dd?]}]
   (cmd/ft-drop-index conn index-name dd?)))
