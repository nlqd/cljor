(ns openrouter.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [openrouter.schema :as schema]))

(deftest config-schema
  (testing "happy path"
    (is (m/validate schema/Config
                    {:openrouter.config/api-key    "sk-test"
                     :openrouter.config/base-url   "https://x"
                     :openrouter.config/timeout-ms 1000})))

  (testing "defaults applied by coerce!"
    (let [c (schema/coerce! schema/Config {:openrouter.config/api-key "sk-test"})]
      (is (= "https://openrouter.ai/api/v1" (:openrouter.config/base-url c)))
      (is (= 30000 (:openrouter.config/timeout-ms c)))))

  (testing "missing api-key throws anomaly"
    (try
      (schema/coerce! schema/Config {})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= :incorrect (:cognitect.anomalies/category (ex-data e))))))))

(deftest anomaly-schema
  (is (m/validate schema/Anomaly
                  {:cognitect.anomalies/category :busy
                   :openrouter.anomaly/status    429}))
  (is (not (m/validate schema/Anomaly
                       {:cognitect.anomalies/category :nonsense}))))

(deftest request-envelope-schema
  (is (m/validate schema/RequestEnvelope
                  {:openrouter.request/method  :post
                   :openrouter.request/path    "/chat/completions"
                   :openrouter.request/body    {:model "x" :messages []}
                   :openrouter.request/stream? true}))

  (testing "stream? default"
    (let [env (schema/coerce! schema/RequestEnvelope
                              {:openrouter.request/method :get
                               :openrouter.request/path   "/models"})]
      (is (false? (:openrouter.request/stream? env))))))

(deftest message-and-chat-request
  (is (m/validate schema/Message {:role "user" :content "hi"}))
  (is (not (m/validate schema/Message {:role "stranger" :content "hi"})))
  (is (m/validate schema/ChatRequest
                  {:model "openai/gpt-4o-mini"
                   :messages [{:role "user" :content "hi"}]})))

(deftest stream-delta
  (is (m/validate schema/StreamDelta
                  {:choices [{:delta {:content "hi"} :finish_reason nil}]}))
  (testing "finish_reason chunk with no content"
    (is (m/validate schema/StreamDelta
                    {:choices [{:delta {} :finish_reason "stop"}]}))))
