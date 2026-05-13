(ns openrouter.client
  (:require [hato.client :as hato]
            [jsonista.core :as json]
            [openrouter.error :as error]))

(defn build-http-client [config]
  (hato/build-http-client {:connect-timeout (:timeout-ms config)
                            :redirect-policy :always}))

(defn- base-headers [config]
  (cond-> {"Authorization" (str "Bearer " (:api-key config))
           "Content-Type"  "application/json"
           "Accept"        "application/json"
           "User-Agent"    "openrouter-clj/0.1.0"}
    (:http-referer config) (assoc "HTTP-Referer" (:http-referer config))
    (:x-title config)      (assoc "X-Title" (:x-title config))))

(defn- url [config path]
  (str (:base-url config) path))

(defn get!
  [config http-client path]
  (let [resp (hato/get (url config path)
                       {:http-client   http-client
                        :headers       (base-headers config)
                        :as            :string
                        :timeout       (:timeout-ms config)})]
    (-> resp
        (update :body #(json/read-value % json/keyword-keys-object-mapper))
        error/check-response!)))

(defn post!
  [config http-client path body]
  (let [resp (hato/post (url config path)
                        {:http-client   http-client
                         :headers       (base-headers config)
                         :body          (json/write-value-as-string body)
                         :as            :string
                         :timeout       (:timeout-ms config)})]
    (-> resp
        (update :body #(json/read-value % json/keyword-keys-object-mapper))
        error/check-response!)))

(defn post-stream!
  "Returns a raw InputStream for SSE consumption."
  [config http-client path body]
  (let [resp (hato/post (url config path)
                        {:http-client   http-client
                         :headers       (base-headers config)
                         :body          (json/write-value-as-string body)
                         :as            :stream
                         :timeout       (:timeout-ms config)})]
    (when-not (<= 200 (:status resp) 299)
      (throw (error/http-error (:status resp) {})))
    (:body resp)))
