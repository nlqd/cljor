(ns openrouter.models
  (:require [openrouter.client :as client]))

(defn list-models
  "Returns {:data [{:id ... :name ... } ...]}."
  [{:keys [config http-client]}]
  (client/get! config http-client "/models"))

(defn model-count
  "Returns {:data {:total ...}}."
  [{:keys [config http-client]}]
  (client/get! config http-client "/models/count"))
