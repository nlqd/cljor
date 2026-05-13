(ns openrouter.sse
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [jsonista.core :as json])
  (:import [java.io BufferedReader InputStreamReader InputStream]))

(def ^:private data-prefix "data: ")
(def ^:private done-sentinel "[DONE]")

(defn event-stream->chan
  "Parses an SSE InputStream into a core.async channel of decoded maps.
   The channel closes after [DONE] or stream end. Errors are put as Throwable."
  [^InputStream input-stream & {:keys [buf-size] :or {buf-size 32}}]
  (let [ch (async/chan buf-size)]
    (async/thread
      (try
        (with-open [reader (BufferedReader. (InputStreamReader. input-stream "UTF-8"))]
          (loop []
            (when-let [line (.readLine reader)]
              (if (str/starts-with? line data-prefix)
                (let [payload (subs line (count data-prefix))]
                  (if (= payload done-sentinel)
                    nil ; close naturally after loop exits
                    (do
                      (async/>!! ch (json/read-value payload json/keyword-keys-object-mapper))
                      (recur))))
                (recur)))))
        (catch Throwable t
          (async/>!! ch t)))
      (async/close! ch))
    ch))
