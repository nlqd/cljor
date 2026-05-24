(ns openrouter.web
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [hiccup2.core :refer [html]]
            [jsonista.core :as json]
            [reitit.coercion.malli :as rcm]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.core.protocols :as proto]
            [openrouter.chat :as chat]))

;;; ── HTML page ────────────────────────────────────────────────────────────────

(defn- page []
  (str "<!DOCTYPE html>"
       (html
        [:html {:lang "en"}
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:title "OpenRouter Chat"]
          [:link {:rel "stylesheet" :href "/style.css"}]]
         [:body
          [:h1 "OpenRouter Chat"]
          [:div {:id "messages"}]
          [:div {:id "input-area"}
           [:input {:id "message-input" :type "text"
                    :placeholder "Type a message..." :autocomplete "off"}]
           [:button {:id "send-btn" :type "button"} "Send"]]
          [:script {:src "/app.js"}]]])))

;;; ── SSE encoding ─────────────────────────────────────────────────────────────

(defn- sse-event [type data]
  (str "event: " type "\n"
       (->> (str/split-lines (str data))
            (map #(str "data: " %))
            (str/join "\n"))
       "\n\n"))

(defn- safe-write! [w s]
  (try (.write w s) (.flush w) (catch java.io.IOException _)))

(defn- anomaly->payload
  "Encode the anomaly's category and message as a JSON string for the SSE
   `done` event. Empty string when no anomaly is present."
  [a]
  (if-let [cat (:cognitect.anomalies/category a)]
    (json/write-value-as-string
     (cond-> {:category cat}
       (:cognitect.anomalies/message a)
       (assoc :message (:cognitect.anomalies/message a))))
    ""))

(defn- event->token
  "Extracts the token string from a delta event, or nil if the event has
   none (finish_reason chunks, errors, etc.)."
  [event]
  (when-not (instance? Throwable event)
    (get-in event [:choices 0 :delta :content])))

(defn- write-tokens!
  "Drains the channel of complete-stream events, writing one SSE `token`
   event per non-empty content delta. Returns the anomaly map seen on the
   channel, or nil if the stream ended cleanly."
  [w ch]
  (loop [anomaly nil]
    (if-let [event (<!! ch)]
      (if (instance? Throwable event)
        (recur (ex-data event))
        (do (when-let [token (event->token event)]
              (safe-write! w (sse-event "token" token)))
            (recur anomaly)))
      anomaly)))

(defn- write-sse!
  "Pipe a single chat-completion stream to one SSE response: token events
   followed by exactly one `done` event carrying any anomaly payload."
  [client model q out]
  (with-open [w (java.io.OutputStreamWriter. out "UTF-8")]
    (let [anomaly (try
                    (write-tokens! w (chat/complete-stream
                                      client
                                      {:model    model
                                       :messages [{:role "user" :content q}]}))
                    (catch Exception e (ex-data e)))]
      (safe-write! w (sse-event "done" (anomaly->payload anomaly))))))

(defn- stream-response [client model q]
  {:status  200
   :headers {"Content-Type"      "text/event-stream"
             "Cache-Control"     "no-cache"
             "Connection"        "keep-alive"
             "X-Accel-Buffering" "no"}
   :body    (reify proto/StreamableResponseBody
              (write-body-to-stream [_ _ out]
                (write-sse! client model q out)))})

;;; ── route table ──────────────────────────────────────────────────────────────

(defn- home-handler [_req]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (page)})

(defn- stream-handler
  "Closes over the client + model from `make-handler`."
  [client model]
  (fn [req]
    (let [q (-> req :parameters :query :q)]
      (stream-response client model q))))

(defn- routes
  [client model]
  [["/"        {:get {:handler home-handler}}]
   ["/stream"  {:get {:parameters {:query [:map [:q [:string {:min 1}]]]}
                      :handler    (stream-handler client model)}}]])

(def ^:private router-data
  {:coercion   rcm/coercion
   :middleware [parameters/parameters-middleware
                rrc/coerce-exceptions-middleware
                rrc/coerce-request-middleware]})

(defn- wrap-json-body
  "Coercion errors return Clojure maps as :body. Jetty needs bytes, so we
   JSON-encode any map-bodied response on the way out. Streaming bodies
   and string/byte bodies pass through untouched."
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (if (map? (:body resp))
        (-> resp
            (update :body json/write-value-as-string)
            (assoc-in [:headers "Content-Type"] "application/json; charset=utf-8"))
        resp))))

(defn make-handler
  "Build a reitit ring handler closing over the OpenRouter client and model.
   No global state; call from a Component during start."
  [{::keys [client model]}]
  (-> (ring/ring-handler
       (ring/router (routes client model) {:data router-data})
       (ring/routes
        (ring/create-resource-handler {:path "/"})
        (ring/create-default-handler)))
      wrap-json-body))
