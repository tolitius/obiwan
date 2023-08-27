(ns obiwan.commands
  (:refer-clojure :exclude [get])
  (:require [obiwan.tools :as t])
  (:import [redis.clients.jedis.params SetParams]
           [redis.clients.jedis.resps Tuple]))

;; TODO: add params for all commands
;;       explicit is good, no macros

(defonce MODULE (t/make-protocol-command "MODULE"))

(defn ->set-params [{:keys [xx nx px ex exat pxat keepttl get]}]
  (cond-> (SetParams/setParams)
    xx (.xx)
    nx (.nx)
    px (.px px)
    ex (.ex ex)
    exat (.exAt exat)
    pxat (.pxAt pxat)
    keepttl (.keepttl)
    get (.get)))

(defn <-tuple
  "this fn transforms the data from redis tuple to clojure map"
  [^Tuple v]
  {(.getScore v) (.getElement v)})
