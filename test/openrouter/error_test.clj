(ns openrouter.error-test
  (:require [clojure.test :refer [are deftest is testing]]
            [openrouter.error :as error]
            [openrouter.schema :as schema]
            [malli.core :as m]))

(deftest category-for-mapping
  (are [status category] (= category (error/category-for status))
    400 :incorrect
    401 :forbidden
    403 :forbidden
    404 :not-found
    422 :incorrect
    429 :busy
    500 :fault
    502 :unavailable
    503 :unavailable
    504 :unavailable)

  (testing "ranges fall through"
    (is (= :incorrect (error/category-for 418)))
    (is (= :fault     (error/category-for 599)))))

(deftest anomaly-conforms-to-schema
  (let [a (error/anomaly {:category :busy :message "slow down" :status 429})]
    (is (m/validate schema/Anomaly a))
    (is (= :busy (:cognitect.anomalies/category a)))
    (is (= 429   (:openrouter.anomaly/status a)))))

(deftest http-anomaly-uses-error-message
  (let [a (error/http-anomaly 429 {:error {:message "rate limited"}})]
    (is (= "rate limited" (:cognitect.anomalies/message a)))
    (is (= :busy          (:cognitect.anomalies/category a)))))

(deftest check-response!-passes-2xx-throws-otherwise
  (is (= {:ok 1} (error/check-response! {:status 200 :body {:ok 1}})))
  (let [thrown (try (error/check-response! {:status 404 :body {}})
                    (catch clojure.lang.ExceptionInfo e e))]
    (is (= :not-found (:cognitect.anomalies/category (ex-data thrown))))
    (is (= 404        (:openrouter.anomaly/status   (ex-data thrown))))))
