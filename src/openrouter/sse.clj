(ns openrouter.sse
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [jsonista.core :as json])
  (:import [java.io BufferedReader InputStreamReader InputStream]))

(def ^:private data-prefix "data: ")
(def ^:private done-sentinel "[DONE]")

(def ^:private event-xform
  "Transducer over raw SSE lines: keep `data:` lines, strip the prefix,
   stop at [DONE], decode JSON."
  (comp
   (filter #(str/starts-with? % data-prefix))
   (map    #(subs % (count data-prefix)))
   (take-while #(not= done-sentinel %))
   (map #(json/read-value % json/keyword-keys-object-mapper))))

(defn event-stream->chan
  "Parses an SSE InputStream into a core.async channel of decoded maps.
   The channel closes after [DONE] or stream end. Parse errors land on
   the channel as Throwables via the channel's exception handler."
  [^InputStream input-stream & {:keys [buf-size] :or {buf-size 32}}]
  (let [ch (async/chan buf-size event-xform identity)]
    (async/thread
      (try
        (with-open [reader (BufferedReader. (InputStreamReader. input-stream "UTF-8"))]
          (doseq [line (line-seq reader)]
            (async/>!! ch line)))
        (catch Throwable t
          (async/>!! ch t))
        (finally
          (async/close! ch))))
    ch))
