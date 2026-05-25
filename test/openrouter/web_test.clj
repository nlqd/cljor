(ns openrouter.web-test
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [openrouter.chat :as chat]
            [openrouter.config :as-alias config]
            [openrouter.mock-openrouter :as mock]
            [openrouter.system :as system]
            [openrouter.web :as web])
  (:import [java.io BufferedReader InputStreamReader]
           [java.net HttpURLConnection URL]))

;;; ── helpers ──────────────────────────────────────────────────────────────────

(defn- server-port [jetty]
  (-> jetty .getConnectors (aget 0) .getLocalPort))

(defn- read-sse [stream]
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

(defn- get-stream [url-str]
  (let [conn (cast HttpURLConnection (.openConnection (URL. url-str)))]
    (.setRequestProperty conn "Accept" "text/event-stream")
    (.connect conn)
    (.getInputStream conn)))

(defn- count-events [parsed type]
  (count (filter #(= type (:event %)) parsed)))

;;; ── fixture ──────────────────────────────────────────────────────────────────

(def ^:dynamic *system* nil)
(def ^:dynamic *port* nil)

(defn with-system [f]
  (let [sys (component/start
             (system/system {::config/api-key "sk-test"
                             ::web/port       0}))]
    (binding [*system* sys
              *port*   (server-port (-> sys ::system/web :jetty))]
      (try (f) (finally (component/stop sys))))))

(use-fixtures :each with-system)

;;; ── route smoke tests ────────────────────────────────────────────────────────

(deftest home-page-returns-200
  (let [conn (cast HttpURLConnection (.openConnection (URL. (str "http://localhost:" *port* "/"))))]
    (.connect conn)
    (is (= 200 (.getResponseCode conn)))
    (is (str/includes? (.getContentType conn) "text/html"))))

(defn- read-body [conn]
  (let [s (or (try (.getInputStream conn) (catch Exception _ nil))
              (.getErrorStream conn))]
    (slurp s)))

(deftest stream-missing-query-returns-400
  (let [conn (cast HttpURLConnection (.openConnection (URL. (str "http://localhost:" *port* "/stream"))))]
    (.connect conn)
    (is (= 400 (.getResponseCode conn)))
    (testing "coercion error body is JSON describing the missing key"
      (is (str/includes? (.getContentType conn) "application/json"))
      (is (str/includes? (read-body conn) "request-coercion")))))

;;; ── SSE encoding tests (redef the streaming source) ──────────────────────────

(deftest stream-delivers-token-events
  (testing "each token from complete-stream arrives as an SSE token event, followed by exactly one done"
    (let [tokens ["Hello" ", " "world" "!"]
          ch     (async/chan 10)]
      (doseq [t tokens]
        (async/>!! ch {:choices [{:delta {:content t}}]}))
      (async/close! ch)
      (with-redefs [chat/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)
              token-events (filter #(= "token" (:event %)) parsed)]
          (is (= tokens (mapv :data token-events)))
          (is (= 1 (count-events parsed "done"))))))))

(deftest stream-ends-with-exactly-one-done-on-error
  (testing "a Throwable on the channel produces exactly one done event"
    (let [ch (async/chan 2)]
      (async/>!! ch (ex-info "boom" {}))
      (async/close! ch)
      (with-redefs [chat/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)]
          (is (= 1 (count-events parsed "done"))))))))

(deftest done-event-carries-anomaly-category
  (testing "when the upstream error has anomaly ex-data, done event echoes the category"
    (let [ch (async/chan 2)]
      (async/>!! ch (ex-info "rate limited"
                             {:cognitect.anomalies/category :busy
                              :cognitect.anomalies/message  "rate limited"
                              :openrouter.anomaly/status    429}))
      (async/close! ch)
      (with-redefs [chat/complete-stream (fn [_ _] ch)]
        (let [events     (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed     (read-sse events)
              done-event (first (filter #(= "done" (:event %)) parsed))]
          (is (some? done-event))
          (is (str/includes? (:data done-event) "busy"))
          (is (str/includes? (:data done-event) "rate limited")))))))

(deftest stream-skips-nil-content-deltas
  (testing "events with no :content field (e.g. finish_reason deltas) are silently dropped"
    (let [ch (async/chan 5)]
      (async/>!! ch {:choices [{:delta {:content "hi"}}]})
      (async/>!! ch {:choices [{:delta {} :finish_reason "stop"}]})
      (async/close! ch)
      (with-redefs [chat/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)
              token-events (filter #(= "token" (:event %)) parsed)]
          (is (= ["hi"] (mapv :data token-events))))))))

(deftest stream-handles-newlines-in-tokens
  (testing "tokens containing newlines are encoded as multiple data: lines"
    (let [ch (async/chan 3)]
      (async/>!! ch {:choices [{:delta {:content "line1\nline2"}}]})
      (async/close! ch)
      (with-redefs [chat/complete-stream (fn [_ _] ch)]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)
              token-events (filter #(= "token" (:event %)) parsed)]
          (is (= "line1\nline2" (:data (first token-events)))))))))

;;; ── true e2e: real HTTP through the full stack ───────────────────────────────

(deftest e2e-stream-via-mock-openrouter-server
  (testing "chat UI proxies tokens from a real mock OpenRouter HTTP server"
    (let [tokens ["Hello" ", " "world" "!"]
          [mock-server mock-port] (mock/start! tokens)
          sys (component/start
               (system/system {::config/api-key  "sk-test"
                               ::config/base-url (str "http://localhost:" mock-port)
                               ::web/port        0}))
          web-port (server-port (-> sys ::system/web :jetty))]
      (try
        (let [events       (read-sse (get-stream (str "http://localhost:" web-port "/stream?q=hello")))
              token-events (filter #(= "token" (:event %)) events)]
          (is (= tokens (mapv :data token-events)))
          (is (some #(= "done" (:event %)) events)))
        (finally
          (component/stop sys)
          (mock/stop! [mock-server]))))))

(deftest stream-sends-keepalive-during-idle
  (testing "a slow stream produces keepalive events between tokens"
    (let [ch (async/chan 10)]
      (async/>!! ch {:choices [{:delta {:content "hi"}}]})
      (async/thread
        (Thread/sleep 2500)
        (async/close! ch))
      (with-redefs [chat/complete-stream (fn [_ _] ch)
                    web/keepalive-ms     1000]
        (let [events (get-stream (str "http://localhost:" *port* "/stream?q=hi"))
              parsed (read-sse events)]
          (is (>= (count-events parsed "token") 1))
          (is (>= (count-events parsed "keepalive") 1))
          (is (= 1 (count-events parsed "done"))))))))
