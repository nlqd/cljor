(ns openrouter.models
  (:require [openrouter.request :as request]))

(defn list-models
  "Returns {:data [{:id ... :name ... } ...]}."
  [client]
  (request/execute! client {:method :get :path "/models"}))

(defn model-count
  "Returns {:data {:total ...}}."
  [client]
  (request/execute! client {:method :get :path "/models/count"}))
