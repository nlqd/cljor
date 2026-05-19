(ns openrouter.core
  (:require [openrouter.chat :as chat]
            [openrouter.client :as client]
            [openrouter.config :as config]
            [openrouter.models :as models]))

(defn make-client
  "Build a client value from opts. See `openrouter.config/make-client`
   for the opts keys."
  [opts]
  (let [cfg (config/make-client opts)]
    {:openrouter.client/config      cfg
     :openrouter.client/http-client (client/build-http-client cfg)}))

(def complete        chat/complete)
(def complete-stream chat/complete-stream)
(def list-models     models/list-models)
(def model-count     models/model-count)
