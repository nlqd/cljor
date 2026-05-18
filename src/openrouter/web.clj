(ns openrouter.web
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [compojure.core :refer [GET routes]]
            [compojure.route :as route]
            [hiccup2.core :refer [html]]
            [ring.core.protocols :as proto]
            [ring.middleware.params :refer [wrap-params]]
            [openrouter.core :as or-client]))

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

(defn- sse-event [type data]
  (str "event: " type "\n"
       (->> (str/split-lines (str data))
            (map #(str "data: " %))
            (str/join "\n"))
       "\n\n"))

(defn- safe-write! [w s]
  (try (.write w s) (.flush w) (catch Exception _)))

(defn- write-sse! [client model q out]
  (with-open [w (java.io.OutputStreamWriter. out "UTF-8")]
    (try
      (let [ch (or-client/complete-stream client {:model    model
                                                  :messages [{:role "user" :content q}]})]
        (loop []
          (when-let [event (<!! ch)]
            (when-not (instance? Throwable event)
              (when-let [token (get-in event [:choices 0 :delta :content])]
                (safe-write! w (sse-event "token" token)))
              (recur)))))
      (catch Exception _ nil)
      (finally
        (safe-write! w (sse-event "done" ""))))))

(defn stream-response
  "Builds a Ring SSE response using StreamableResponseBody."
  [client model q]
  {:status  200
   :headers {"Content-Type"      "text/event-stream"
             "Cache-Control"     "no-cache"
             "Connection"        "keep-alive"
             "X-Accel-Buffering" "no"}
   :body    (reify proto/StreamableResponseBody
              (write-body-to-stream [_ _ out]
                (write-sse! client model q out)))})

(defn make-handler
  "Builds a Ring handler closing over the OpenRouter client and model.
   No global state — call from a system/component during start."
  [{:keys [client model]}]
  (-> (routes
       (GET "/" [] {:status 200 :headers {"Content-Type" "text/html"} :body (page)})
       (GET "/stream" [q]
         (if (and client (seq q))
           (stream-response client model q)
           {:status 400 :body "Missing query or client not initialized"}))
       (route/resources "/")
       (route/not-found "Not found"))
      wrap-params))
