(ns openrouter.sse-test
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [openrouter.sse :as sse])
  (:import [java.io ByteArrayInputStream]))

(defn- stream-of [& lines]
  (ByteArrayInputStream. (.getBytes (str (str/join "\n" lines) "\n") "UTF-8")))

(defn- make-event [s]
  (str "data: {\"choices\":[{\"delta\":{\"content\":\"" s "\"}}]}"))

(deftest parses-multiple-events-until-done
  (let [ch (sse/event-stream->chan
            (stream-of (make-event "a") (make-event "b") (make-event "c")
                       "data: [DONE]"))]
    (is (= "a" (get-in (<!! ch) [:choices 0 :delta :content])))
    (is (= "b" (get-in (<!! ch) [:choices 0 :delta :content])))
    (is (= "c" (get-in (<!! ch) [:choices 0 :delta :content])))
    (is (nil? (<!! ch)))))

(deftest skips-blank-and-comment-lines
  (let [ch (sse/event-stream->chan
            (stream-of "" ": comment" (make-event "hi") "data: [DONE]"))]
    (is (= "hi" (get-in (<!! ch) [:choices 0 :delta :content])))
    (is (nil? (<!! ch)))))

(deftest closes-channel-on-stream-end-without-done
  (let [ch (sse/event-stream->chan (stream-of (make-event "x")))]
    (<!! ch)
    (is (nil? (<!! ch)))))
