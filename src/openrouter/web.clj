(ns openrouter.web
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :as route]
            [hiccup.core :refer [html]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer [wrap-params]]
            [openrouter.core :as or-client])
  (:gen-class))

(defonce !client (atom nil))

(def ^:private default-model "openai/gpt-4o-mini")

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
           [:script {:src "https://cdn.jsdelivr.net/npm/marked/marked.min.js"}]
           [:script {:src "/app.js"}]]])))

(defn- sse-event [type data]
  (str "event: " type "\n"
       (->> (str/split-lines (str data))
            (map #(str "data: " %))
            (str/join "\n"))
       "\n\n"))

(defn stream-response
  "Builds a Ring SSE response that consumes a complete-stream channel.
   Exported (non-private) for testability."
  [client model q]
  (let [pout (java.io.PipedOutputStream.)
        pin  (java.io.PipedInputStream. pout)]
    (future
      (with-open [w (java.io.OutputStreamWriter. pout "UTF-8")]
        (try
          (let [ch (or-client/complete-stream client {:model    model
                                                      :messages [{:role "user" :content q}]})]
            (loop []
              (when-let [event (<!! ch)]
                (if (instance? Throwable event)
                  (do (.write w (sse-event "done" "")) (.flush w))
                  (do (when-let [token (get-in event [:choices 0 :delta :content])]
                        (.write w (sse-event "token" token))
                        (.flush w))
                      (recur)))))
            (.write w (sse-event "done" ""))
            (.flush w))
          (catch Exception _
            (try (.write w (sse-event "done" "")) (.flush w) (catch Exception _))))))
    {:status  200
     :headers {"Content-Type"      "text/event-stream"
               "Cache-Control"     "no-cache"
               "Connection"        "keep-alive"
               "X-Accel-Buffering" "no"}
     :body    pin}))

(defroutes app-routes
  (GET "/" [] {:status 200 :headers {"Content-Type" "text/html"} :body (page)})
  (GET "/stream" [q]
    (let [client @!client
          model  (or (System/getenv "OPENROUTER_MODEL") default-model)]
      (if (and client (seq q))
        (stream-response client model q)
        {:status 400 :body "Missing query or client not initialized"})))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (-> #'app-routes wrap-params))

(defn start-server!
  "Starts Jetty. Returns the server object (useful for tests)."
  [port]
  (jetty/run-jetty #'app {:port port :join? false}))

(defn -main [& _args]
  (let [api-key (System/getenv "OPENROUTER_API_KEY")]
    (when-not api-key
      (throw (ex-info "OPENROUTER_API_KEY env var is required" {})))
    (reset! !client (or-client/make-client {:api-key      api-key
                                            :http-referer "http://localhost:3000"
                                            :x-title      "OpenRouter Chat Demo"}))
    (println "Listening on http://localhost:3000")
    (.join (start-server! 3000))))
