(ns openrouter.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [openrouter.config :as-alias config]
            [openrouter.schema :as schema]))

(deftest config-schema
  (testing "happy path"
    (is (m/validate schema/Config
                    {::config/api-key    "sk-test"
                     ::config/base-url   "https://x"
                     ::config/timeout-ms 1000})))

  (testing "defaults applied by coerce!"
    (let [c (schema/coerce! schema/Config {::config/api-key "sk-test"})]
      (is (= "https://openrouter.ai/api/v1" (::config/base-url c)))
      (is (= 30000 (::config/timeout-ms c)))))

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
                  {:method  :post
                   :path    "/chat/completions"
                   :body    {:model "x" :messages []}
                   :stream? true}))

  (testing "stream? default"
    (let [env (schema/coerce! schema/RequestEnvelope
                              {:method :get :path "/models"})]
      (is (false? (:stream? env))))))

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
