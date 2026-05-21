(ns openrouter.chat
  (:require [openrouter.request :as request]
            [openrouter.sse :as sse]))

(defn complete
  "Blocking chat completion. Returns the full API response map."
  [client params]
  (request/execute! client
                    {:method :post
                     :path   "/chat/completions"
                     :body   params}))

(defn complete-stream
  "Streaming chat completion. Returns a core.async channel of delta maps.
   Channel closes after the last token or on error (error is put as Throwable)."
  [client params]
  (-> (request/execute!
       client
       {:method  :post
        :path    "/chat/completions"
        :body    (assoc params :stream true)
        :stream? true})
      sse/event-stream->chan))
