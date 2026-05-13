(ns openrouter.core
  (:require [openrouter.chat :as chat]
            [openrouter.client :as client]
            [openrouter.config :as config]
            [openrouter.models :as models]))

(defn make-client
  "Build a client map from opts. :api-key is required.
   Optional: :base-url :http-referer :x-title :timeout-ms"
  [opts]
  (let [cfg (config/make-client opts)]
    {:config      cfg
     :http-client (client/build-http-client cfg)}))

(def complete        chat/complete)
(def complete-stream chat/complete-stream)
(def list-models     models/list-models)
(def model-count     models/model-count)
