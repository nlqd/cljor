(ns openrouter.error)

(defn api-error [status body]
  (ex-info (or (get-in body [:error :message]) "OpenRouter API error")
           {:type    :openrouter/api-error
            :status  status
            :body    body}))

(defn http-error [status body]
  (ex-info (str "HTTP error " status)
           {:type   :openrouter/http-error
            :status status
            :body   body}))

(defn check-response!
  "Throws ex-info on non-2xx responses. Returns body map on success."
  [{:keys [status body]}]
  (cond
    (<= 200 status 299) body
    (contains? body :error) (throw (api-error status body))
    :else (throw (http-error status body))))
