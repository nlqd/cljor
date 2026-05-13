(ns openrouter.sse-test
  (:require [clojure.core.async :refer [<!! close!]]
            [clojure.test :refer [deftest is testing]]
            [openrouter.sse :as sse])
  (:import [java.io ByteArrayInputStream]))

(defn- stream-of [& lines]
  (ByteArrayInputStream. (.getBytes (str (clojure.string/join "\n" lines) "\n") "UTF-8")))

(deftest parses-events
  (let [payload "{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}"
        ch (sse/event-stream->chan
            (stream-of (str "data: " payload)
                       "data: [DONE]"))]
    (is (= {:choices [{:delta {:content "hello"}}]}
           (<!! ch)))
    (is (nil? (<!! ch)))))

(deftest skips-blank-and-comment-lines
  (let [payload "{\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}"
        ch (sse/event-stream->chan
            (stream-of ""
                       ": comment"
                       (str "data: " payload)
                       "data: [DONE]"))]
    (is (= {:choices [{:delta {:content "hi"}}]} (<!! ch)))
    (is (nil? (<!! ch)))))

(deftest closes-channel-on-stream-end-without-done
  (let [payload "{\"choices\":[{\"delta\":{\"content\":\"x\"}}]}"
        ch (sse/event-stream->chan
            (stream-of (str "data: " payload)))]
    (<!! ch)
    (is (nil? (<!! ch)))))

(deftest multiple-events
  (let [make-event (fn [s] (str "data: {\"choices\":[{\"delta\":{\"content\":\"" s "\"}}]}"))
        ch (sse/event-stream->chan
            (stream-of (make-event "a")
                       (make-event "b")
                       (make-event "c")
                       "data: [DONE]"))]
    (is (= "a" (get-in (<!! ch) [:choices 0 :delta :content])))
    (is (= "b" (get-in (<!! ch) [:choices 0 :delta :content])))
    (is (= "c" (get-in (<!! ch) [:choices 0 :delta :content])))
    (is (nil? (<!! ch)))))
