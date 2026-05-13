(ns openrouter.chat
  (:require [openrouter.client :as client]
            [openrouter.sse :as sse]))

(defn complete
  "Blocking chat completion. Returns the full API response map."
  [{:keys [config http-client]} params]
  (client/post! config http-client "/chat/completions" params))

(defn complete-stream
  "Streaming chat completion. Returns a core.async channel of delta maps.
   Channel closes after the last token or on error (error is put as Throwable)."
  [{:keys [config http-client]} params]
  (let [stream (client/post-stream! config http-client "/chat/completions"
                                    (assoc params :stream true))]
    (sse/event-stream->chan stream)))
