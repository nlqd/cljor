(ns openrouter.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [openrouter.config :as config]))

(deftest make-client-requires-api-key
  (is (thrown? Exception (config/make-client {})))
  (is (thrown? Exception
               (config/make-client {:openrouter.config/base-url "http://x"}))))

(deftest make-client-defaults
  (let [c (config/make-client {:openrouter.config/api-key "sk-test"})]
    (is (= "sk-test" (:openrouter.config/api-key c)))
    (is (= "https://openrouter.ai/api/v1" (:openrouter.config/base-url c)))
    (is (= 30000 (:openrouter.config/timeout-ms c)))
    (is (nil? (:openrouter.config/http-referer c)))
    (is (nil? (:openrouter.config/x-title c)))))

(deftest make-client-accepts-overrides
  (let [c (config/make-client
           {:openrouter.config/api-key      "sk-test"
            :openrouter.config/base-url     "http://localhost:8080"
            :openrouter.config/http-referer "https://myapp.com"
            :openrouter.config/x-title      "My App"
            :openrouter.config/timeout-ms   5000})]
    (is (= "http://localhost:8080" (:openrouter.config/base-url c)))
    (is (= "https://myapp.com" (:openrouter.config/http-referer c)))
    (is (= "My App" (:openrouter.config/x-title c)))
    (is (= 5000 (:openrouter.config/timeout-ms c)))))

(deftest invalid-opts-throw-anomaly
  (testing "ex-data is an anomaly with category :incorrect"
    (try
      (config/make-client {})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= :incorrect (:cognitect.anomalies/category (ex-data e))))))))
