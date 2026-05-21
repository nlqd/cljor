(ns openrouter.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [openrouter.config :as config]))

(deftest make-client-requires-api-key
  (is (thrown? Exception (config/make-client {})))
  (is (thrown? Exception
               (config/make-client {::config/base-url "http://x"}))))

(deftest make-client-defaults
  (let [c (config/make-client {::config/api-key "sk-test"})]
    (is (= "sk-test" (::config/api-key c)))
    (is (= "https://openrouter.ai/api/v1" (::config/base-url c)))
    (is (= 30000 (::config/timeout-ms c)))
    (is (nil? (::config/http-referer c)))
    (is (nil? (::config/x-title c)))))

(deftest make-client-accepts-overrides
  (let [c (config/make-client
           {::config/api-key      "sk-test"
            ::config/base-url     "http://localhost:8080"
            ::config/http-referer "https://myapp.com"
            ::config/x-title      "My App"
            ::config/timeout-ms   5000})]
    (is (= "http://localhost:8080" (::config/base-url c)))
    (is (= "https://myapp.com"     (::config/http-referer c)))
    (is (= "My App"                (::config/x-title c)))
    (is (= 5000                    (::config/timeout-ms c)))))

(deftest invalid-opts-throw-anomaly
  (testing "ex-data is an anomaly with category :incorrect"
    (try
      (config/make-client {})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= :incorrect (:cognitect.anomalies/category (ex-data e))))))))
