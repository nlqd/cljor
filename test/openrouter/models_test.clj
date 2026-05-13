(ns openrouter.models-test
  (:require [clojure.test :refer [deftest is]]
            [openrouter.client :as client]
            [openrouter.models :as models]))

(def ^:private fake-client
  {:config      {:api-key "sk-test" :base-url "x" :timeout-ms 1000}
   :http-client ::fake})

(deftest list-models-calls-correct-path
  (with-redefs [client/get! (fn [_cfg _http path]
                              (is (= "/models" path))
                              {:data [{:id "openai/gpt-4o"}]})]
    (is (= {:data [{:id "openai/gpt-4o"}]}
           (models/list-models fake-client)))))

(deftest model-count-calls-correct-path
  (with-redefs [client/get! (fn [_cfg _http path]
                              (is (= "/models/count" path))
                              {:data {:total 300}})]
    (is (= {:data {:total 300}}
           (models/model-count fake-client)))))
