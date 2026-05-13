(ns openrouter.web-test
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures testing]]
            [openrouter.core :as or-client]
            [openrouter.web :as web])
  (:import [java.io BufferedReader InputStreamReader]
           [java.net HttpURLConnection URL]))

;;; ── helpers ──────────────────────────────────────────────────────────────────

(defn- server-port [server]
  (-> server .getConnectors (aget 0) .getLocalPort))

(defn- read-sse
  "Reads SSE events from an InputStream until EOF.
   Returns a vector of {:event type :data value} maps."
  [stream]
  (with-open [rdr (BufferedReader. (InputStreamReader. stream "UTF-8"))]
    (loop [events [] cur {}]
      (if-let [line (.readLine rdr)]
        (cond
          (str/starts-with? line "event: ")
          (recur events (assoc cur :event (subs line 7)))

          (str/starts-with? line "data: ")
          (let [chunk (subs line 6)]
            (recur events (update cur :data #(if % (str % "\n" chunk) chunk))))

          (empty? line)
          (if (seq cur) (recur (conj events cur) {}) (recur events {}))

          :else (recur events cur))
        (cond-> events (seq cur) (conj cur))))))

(defn- get-stream
  "Opens an HTTP GET to url with Accept: text/event-stream.
   Returns the InputStream of the response body."
  [url-str]
  (let [conn (cast HttpURLConnection (.openConnection (URL. url-str)))]
    (.setRequestProperty conn "Accept" "text/event-stream")
    (.connect conn)
    (.getInputStream conn)))

;;; ── fixtures ─────────────────────────────────────────────────────────────────

(def ^:dynamic *port* nil)
(def ^:dynamic *server* nil)

(defn with-server [f]
  ;; Set a dummy client so the /stream guard passes
  (reset! web/!client {:config {:api-key "sk-test" :base-url "x" :timeout-ms 1000}
                       :http-client nil})
  (let [server (web/start-server! 0)
        port   (server-port server)]
    (binding [*server* server *port* port]
      (try (f) (finally (.stop server))))))

(use-fixtures :each with-server)

;;; ── tests ────────────────────────────────────────────────────────────────────

(deftest home-page-returns-200
  (let [conn (cast HttpURLConnection (.openConnection (URL. (str "http://localhost:" *port* "/"))))]
    (.connect conn)
    (is (= 200 (.getResponseCode conn)))
    (is (str/includes? (.getContentType conn) "text/html"))))

(deftest stream-missing-query-returns-400
  (let [conn (cast HttpURLConnection (.openConnection (URL. (str "http://localhost:" *port* "/stream"))))]
    (.connect conn)
    (is (= 400 (.getResponseCode conn)))))

(deftest stream-delivers-token-events
  (testing "each token from complete-stream arrives as an SSE token event"
    (let [tokens ["Hello" ", " "world" "!"]
          ch     (async/chan 10)]
      (doseq [t tokens]
        (async/>!! ch {:choices [{:delta {:content t}}]}))
      (async/close! ch)
      (with-redefs [or-client/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)
              token-events (filter #(= "token" (:event %)) parsed)]
          (is (= tokens (mapv :data token-events)))
          (is (some #(= "done" (:event %)) parsed)))))))

(deftest stream-ends-with-done-on-error
  (testing "a Throwable on the channel still produces a done event"
    (let [ch (async/chan 2)]
      (async/>!! ch (ex-info "boom" {}))
      (async/close! ch)
      (with-redefs [or-client/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)]
          (is (some #(= "done" (:event %)) parsed)))))))

(deftest stream-skips-nil-content-deltas
  (testing "events with no :content field (e.g. finish_reason deltas) are silently dropped"
    (let [ch (async/chan 5)]
      (async/>!! ch {:choices [{:delta {:content "hi"}}]})
      (async/>!! ch {:choices [{:delta {} :finish_reason "stop"}]})
      (async/close! ch)
      (with-redefs [or-client/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)
              token-events (filter #(= "token" (:event %)) parsed)]
          (is (= ["hi"] (mapv :data token-events))))))))

(deftest stream-handles-newlines-in-tokens
  (testing "tokens containing newlines are encoded as multiple data: lines"
    (let [ch (async/chan 3)]
      (async/>!! ch {:choices [{:delta {:content "line1\nline2"}}]})
      (async/close! ch)
      (with-redefs [or-client/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)
              token-events (filter #(= "token" (:event %)) parsed)]
          ;; SSE joins multi-line data back with \n
          (is (= "line1\nline2" (:data (first token-events)))))))))
