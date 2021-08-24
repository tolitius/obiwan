(ns obiwan.search.core
  (:require [obiwan.core :as oc]
            [obiwan.tools :as t]
            [obiwan.search.command.core :as cmd]
            [obiwan.search.command.create :as cc]))

(defn ft-create [conn index-name opts]
  (cc/create-index conn index-name opts))

(defn ft-list [conn]
  (cmd/ft-list conn))

(defn ft-drop-index
  ([conn index-name]
   (cmd/ft-drop-index conn index-name false))
  ([conn index-name dd?]
   (cmd/ft-drop-index conn index-name dd?)))
