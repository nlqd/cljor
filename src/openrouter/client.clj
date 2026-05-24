(ns openrouter.client
  "The client value. `make-client` builds it; the rest of the codebase
   passes it around as a plain map. Per-request behavior lives in
   `openrouter.request`."
  (:require [hato.client :as hato]
            [openrouter.config :as config]
            [openrouter.schema :as schema]))

(defn- build-http-client [cfg]
  (hato/build-http-client {:connect-timeout (::config/timeout-ms cfg)
                           :redirect-policy :always}))

(defn make-client
  "Build a validated client value from opts. See `openrouter.config/make-client`
   for the opts keys.

   Returns a map conforming to `openrouter.schema/Client`."
  [opts]
  (let [cfg (config/make-client opts)]
    (schema/coerce! schema/Client
                    {:openrouter.client/config      cfg
                     :openrouter.client/http-client (build-http-client cfg)})))
