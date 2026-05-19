(ns openrouter.client
  "Construction of the long-lived HTTP client value. Per-request behavior
   lives in `openrouter.request`."
  (:require [hato.client :as hato]))

(defn build-http-client [config]
  (hato/build-http-client {:connect-timeout (:openrouter.config/timeout-ms config)
                           :redirect-policy :always}))
