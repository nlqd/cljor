(ns openrouter.chat-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [openrouter.chat :as chat]
            [openrouter.client :as client]))

(def ^:private fake-client
  {:config      {:api-key  "sk-test"
                 :base-url "https://openrouter.ai/api/v1"
                 :timeout-ms 30000}
   :http-client ::fake})

(def ^:private completion-response
  {:id      "gen-123"
   :choices [{:message {:role "assistant" :content "Hello!"}}]
   :model   "openai/gpt-4o"})

(deftest complete-delegates-to-post
  (with-redefs [client/post! (fn [_config _http-client path body]
                               (is (= "/chat/completions" path))
                               (is (= "openai/gpt-4o" (:model body)))
                               completion-response)]
    (let [result (chat/complete fake-client {:model    "openai/gpt-4o"
                                             :messages [{:role "user" :content "Hi"}]})]
      (is (= completion-response result)))))

(deftest complete-stream-injects-stream-true
  (with-redefs [client/post-stream! (fn [_config _http-client _path body]
                                      (is (true? (:stream body)))
                                      (java.io.ByteArrayInputStream.
                                       (.getBytes "data: [DONE]\n" "UTF-8")))]
    (let [ch (chat/complete-stream fake-client {:model    "openai/gpt-4o"
                                                :messages [{:role "user" :content "Hi"}]})]
      (is (nil? (clojure.core.async/<!! ch))))))
