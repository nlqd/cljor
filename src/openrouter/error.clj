(ns openrouter.error
  "Errors as data. Every thrown ex-info carries an
   `openrouter.schema/Anomaly` as its ex-data, so callers can branch on
   `(:cognitect.anomalies/category (ex-data e))` instead of pattern-matching
   message strings.")

(def ^:private status->category
  "HTTP status code → Cognitect anomaly category. Anything not listed
   falls through to the range-based default in `category-for`."
  {400 :incorrect
   401 :forbidden
   403 :forbidden
   404 :not-found
   408 :unavailable
   422 :incorrect
   429 :busy
   500 :fault
   501 :unsupported
   502 :unavailable
   503 :unavailable
   504 :unavailable})

(defn category-for
  "Map an HTTP status code to an anomaly category."
  [status]
  (or (get status->category status)
      (cond
        (<= 400 status 499) :incorrect
        (<= 500 status 599) :fault
        :else               :fault)))

(defn anomaly
  "Build a validated Anomaly map. Only `:category` is required."
  [{:keys [category message status body cause]}]
  (cond-> {:cognitect.anomalies/category category}
    message (assoc :cognitect.anomalies/message message)
    status  (assoc :openrouter.anomaly/status   status)
    body    (assoc :openrouter.anomaly/body     body)
    cause   (assoc :openrouter.anomaly/cause    cause)))

(defn http-anomaly
  "Convenience: anomaly built from an HTTP error response."
  [status body]
  (anomaly {:category (category-for status)
            :message  (or (get-in body [:error :message])
                          (str "HTTP error " status))
            :status   status
            :body     body}))

(defn ex-anomaly
  "Wrap an Anomaly map in an ex-info."
  [a]
  (ex-info (or (:cognitect.anomalies/message a) "openrouter error") a))

(defn check-response!
  "Throws ex-info (with anomaly ex-data) on non-2xx. Returns body on success."
  [{:keys [status body]}]
  (if (<= 200 status 299)
    body
    (throw (ex-anomaly (http-anomaly status body)))))

(defn check-status!
  "Streaming variant: throws on non-2xx, returns nil on success."
  [status]
  (when-not (<= 200 status 299)
    (throw (ex-anomaly (http-anomaly status nil)))))
