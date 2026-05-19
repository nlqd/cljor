(ns openrouter.config
  (:require [openrouter.schema :as schema]))

(defn make-client
  "Validate and apply defaults to an opts map.

   Required: :openrouter.config/api-key
   Optional: :openrouter.config/base-url, :openrouter.config/http-referer,
             :openrouter.config/x-title, :openrouter.config/timeout-ms

   Throws ex-info whose ex-data is an `openrouter.schema/Anomaly` on
   invalid input."
  [opts]
  (schema/coerce! schema/Config opts))
