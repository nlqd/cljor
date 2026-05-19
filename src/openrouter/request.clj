(ns openrouter.request
  "Data-first IO layer. Callers build a `RequestEnvelope` and hand it to
   `execute!`; this namespace is the only place that knows about hato."
  (:require [hato.client :as hato]
            [jsonista.core :as json]
            [openrouter.error :as error]
            [openrouter.schema :as schema]))

(defn- url [config path]
  (str (:openrouter.config/base-url config) path))

(defn- base-headers
  [{:openrouter.config/keys [api-key http-referer x-title]}]
  (cond-> {"Authorization" (str "Bearer " api-key)
           "Content-Type"  "application/json"
           "Accept"        "application/json"
           "User-Agent"    "openrouter-clj/0.1.0"}
    http-referer (assoc "HTTP-Referer" http-referer)
    x-title      (assoc "X-Title"      x-title)))

(defn- envelope->hato-opts
  [{:openrouter.client/keys [config http-client]}
   {:openrouter.request/keys [method path body stream?]}]
  (cond-> {:method      method
           :url         (url config path)
           :http-client http-client
           :headers     (base-headers config)
           :timeout     (:openrouter.config/timeout-ms config)
           :as          (if stream? :stream :string)}
    body (assoc :body (json/write-value-as-string body))))

(defn execute!
  "Run a request envelope against a client.

   Returns the parsed JSON body for non-streaming requests, or the raw
   InputStream for streaming requests. Throws ex-info (with anomaly
   ex-data) on non-2xx status or schema-invalid envelopes."
  [client envelope]
  (let [env  (schema/coerce! schema/RequestEnvelope envelope)
        resp (hato/request (envelope->hato-opts client env))]
    (if (:openrouter.request/stream? env)
      (do (error/check-status! (:status resp))
          (:body resp))
      (-> resp
          (update :body #(json/read-value % json/keyword-keys-object-mapper))
          error/check-response!))))
