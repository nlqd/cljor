(ns openrouter.sse-test
  (:require [clojure.core.async :as async :refer [<!!]]
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

(deftest detects-mid-stream-error
  (let [ch (sse/event-stream->chan
            (stream-of
             (make-event "partial")
             "data: {\"id\":\"x\",\"choices\":[{\"delta\":{\"content\":\"\"},\"finish_reason\":\"error\"}],\"error\":{\"code\":\"server_error\",\"message\":\"Provider disconnected\"}}"
             "data: [DONE]"))]
    (is (= "partial" (get-in (<!! ch) [:choices 0 :delta :content])))
    (let [err (<!! ch)]
      (is (instance? Throwable err))
      (is (= :fault (:cognitect.anomalies/category (ex-data err))))
      (is (= "Provider disconnected" (:cognitect.anomalies/message (ex-data err)))))
    (is (nil? (<!! ch)))))

(deftest non-error-finish-reasons-pass-through
  (let [ch (sse/event-stream->chan
            (stream-of
             (make-event "done")
             "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}"
             "data: [DONE]"))]
    (is (some? (<!! ch)))
    (let [stop-event (<!! ch)]
      (is (map? stop-event))
      (is (= "stop" (get-in stop-event [:choices 0 :finish_reason]))))
    (is (nil? (<!! ch)))))

(deftest consumer-cancellation-stops-reading
  (let [pipe-in  (java.io.PipedInputStream.)
        pipe-out (java.io.PipedOutputStream. pipe-in)
        w        (java.io.OutputStreamWriter. pipe-out "UTF-8")
        _        (do (.write w (str (make-event "first") "\n"))
                     (.flush w))
        ch       (sse/event-stream->chan pipe-in)]
    (is (= "first" (get-in (<!! ch) [:choices 0 :delta :content])))
    (async/close! ch)
    ;; Send another line to unblock .readLine — mimics server keepalive.
    ;; The >!! on the closed channel returns false, loop exits, with-open
    ;; closes the InputStream.
    (.write w ": keepalive\n")
    (.flush w)
    (Thread/sleep 100)
    (is (thrown? java.io.IOException
                (do (.write w "more data\n")
                    (.flush w))))))
