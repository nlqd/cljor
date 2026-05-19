(ns openrouter.request-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [hato.client :as hato]
            [jsonista.core :as json]
            [openrouter.core :as or-core]
            [openrouter.request :as request]))

(defn- captured-hato
  "Returns [capture-atom stub]. The stub records its arg and returns `resp`."
  [resp]
  (let [a (atom nil)]
    [a (fn [opts] (reset! a opts) resp)]))

(deftest non-streaming-post-roundtrip
  (let [[seen stub] (captured-hato {:status 200 :body "{\"ok\":true}"})]
    (with-redefs [hato/request stub]
      (let [client (or-core/make-client {:openrouter.config/api-key "sk-test"})
            body   (request/execute! client
                                     {:openrouter.request/method :post
                                      :openrouter.request/path   "/chat/completions"
                                      :openrouter.request/body   {:model "x"}})]
        (testing "decoded body"
          (is (= {:ok true} body)))
        (testing "wire request"
          (is (= :post (:method @seen)))
          (is (str/ends-with? (:url @seen) "/chat/completions"))
          (is (= "{\"model\":\"x\"}" (:body @seen)))
          (is (= "Bearer sk-test" (get-in @seen [:headers "Authorization"])))
          (is (= :string (:as @seen))))))))

(deftest streaming-post-returns-input-stream
  (let [bytes (.getBytes "data: hello\n\n" "UTF-8")
        [_ stub] (captured-hato {:status 200
                                 :body (java.io.ByteArrayInputStream. bytes)})]
    (with-redefs [hato/request stub]
      (let [client (or-core/make-client {:openrouter.config/api-key "sk-test"})
            result (request/execute! client
                                     {:openrouter.request/method  :post
                                      :openrouter.request/path    "/chat/completions"
                                      :openrouter.request/body    {:model "x" :stream true}
                                      :openrouter.request/stream? true})]
        (is (instance? java.io.InputStream result))))))

(deftest non-2xx-throws-anomaly
  (let [[_ stub] (captured-hato {:status 429
                                 :body (json/write-value-as-string
                                        {:error {:message "rate limited"}})})]
    (with-redefs [hato/request stub]
      (let [client (or-core/make-client {:openrouter.config/api-key "sk-test"})
            thrown (try (request/execute! client
                                          {:openrouter.request/method :get
                                           :openrouter.request/path   "/models"})
                        (catch clojure.lang.ExceptionInfo e e))]
        (is (= :busy        (:cognitect.anomalies/category (ex-data thrown))))
        (is (= "rate limited" (:cognitect.anomalies/message (ex-data thrown))))
        (is (= 429          (:openrouter.anomaly/status (ex-data thrown))))))))

(deftest invalid-envelope-rejected
  (let [client (or-core/make-client {:openrouter.config/api-key "sk-test"})]
    (try
      (request/execute! client {:openrouter.request/method :wat
                                :openrouter.request/path   "/x"})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= :incorrect (:cognitect.anomalies/category (ex-data e))))))))

(deftest optional-headers-included
  (let [[seen stub] (captured-hato {:status 200 :body "{}"})]
    (with-redefs [hato/request stub]
      (let [client (or-core/make-client
                    {:openrouter.config/api-key      "sk-test"
                     :openrouter.config/http-referer "https://app.test"
                     :openrouter.config/x-title      "My App"})]
        (request/execute! client {:openrouter.request/method :get
                                  :openrouter.request/path   "/models"})
        (is (= "https://app.test" (get-in @seen [:headers "HTTP-Referer"])))
        (is (= "My App"           (get-in @seen [:headers "X-Title"])))))))
