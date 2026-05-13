(ns openrouter.config)

(def default-base-url "https://openrouter.ai/api/v1")
(def default-timeout-ms 30000)

(defn make-client
  [{:keys [api-key base-url http-referer x-title timeout-ms] :as opts}]
  (when (nil? api-key)
    (throw (ex-info "api-key is required" {:opts opts})))
  {:api-key      api-key
   :base-url     (or base-url default-base-url)
   :http-referer http-referer
   :x-title      x-title
   :timeout-ms   (or timeout-ms default-timeout-ms)})
