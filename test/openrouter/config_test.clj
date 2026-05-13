(ns openrouter.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [openrouter.config :as config]))

(deftest make-client-requires-api-key
  (is (thrown? Exception (config/make-client {})))
  (is (thrown? Exception (config/make-client {:base-url "http://x"}))))

(deftest make-client-defaults
  (let [c (config/make-client {:api-key "sk-test"})]
    (is (= "sk-test" (:api-key c)))
    (is (= config/default-base-url (:base-url c)))
    (is (= config/default-timeout-ms (:timeout-ms c)))
    (is (nil? (:http-referer c)))
    (is (nil? (:x-title c)))))

(deftest make-client-accepts-overrides
  (let [c (config/make-client {:api-key      "sk-test"
                               :base-url     "http://localhost:8080"
                               :http-referer "https://myapp.com"
                               :x-title      "My App"
                               :timeout-ms   5000})]
    (is (= "http://localhost:8080" (:base-url c)))
    (is (= "https://myapp.com" (:http-referer c)))
    (is (= "My App" (:x-title c)))
    (is (= 5000 (:timeout-ms c)))))
