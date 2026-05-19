(ns openrouter.models
  (:require [openrouter.request :as request]))

(defn list-models
  "Returns {:data [{:id ... :name ... } ...]}."
  [client]
  (request/execute! client
                    {:openrouter.request/method :get
                     :openrouter.request/path   "/models"}))

(defn model-count
  "Returns {:data {:total ...}}."
  [client]
  (request/execute! client
                    {:openrouter.request/method :get
                     :openrouter.request/path   "/models/count"}))
