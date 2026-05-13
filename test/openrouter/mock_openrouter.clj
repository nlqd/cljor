(ns openrouter.mock-openrouter
  "A minimal HTTP server that mimics the OpenRouter SSE streaming endpoint.
   Use in tests to exercise the full HTTP client stack without a real API key."
  (:require [jsonista.core :as json]
            [ring.adapter.jetty :as jetty]))

(defn- server-port [server]
  (-> server .getConnectors (aget 0) .getLocalPort))

(defn- sse-chunk [token]
  (str "data: "
       (json/write-value-as-string
         {:id      "mock-1"
          :choices [{:delta {:content token} :finish_reason nil}]})
       "\n\n"))

(defn- stream-body [tokens]
  (let [pout (java.io.PipedOutputStream.)
        pin  (java.io.PipedInputStream. pout)]
    (future
      (with-open [w (java.io.OutputStreamWriter. pout "UTF-8")]
        (doseq [token tokens]
          (.write w (sse-chunk token))
          (.flush w))
        (.write w "data: [DONE]\n\n")
        (.flush w)))
    pin))

(defn handler
  "Ring handler that streams tokens regardless of the request path or body."
  [tokens]
  (fn [_req]
    {:status  200
     :headers {"Content-Type"  "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection"    "keep-alive"}
     :body    (stream-body tokens)}))

(defn start!
  "Start the mock server. Returns [jetty-server port]."
  [tokens]
  (let [server (jetty/run-jetty (handler tokens) {:port 0 :join? false})]
    [server (server-port server)]))

(defn stop! [[server _port]]
  (.stop server))
