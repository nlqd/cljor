(ns openrouter.sse-property-test
  "Property-based tests for the SSE pipeline.

   Two flavors of generators are exercised here:
     - hand-rolled test.check generators (tokens as strings)
     - schema-driven generators via `malli.generator/generator`

   The mix is intentional: hand-rolled gives precise control over awkward
   inputs (newlines, empty strings), malli gives breadth for free."
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [jsonista.core :as json]
            [malli.core :as m]
            [malli.generator :as mg]
            [openrouter.error :as error]
            [openrouter.schema :as schema]
            [openrouter.sse :as sse])
  (:import [java.io ByteArrayInputStream]))

;;; ── wire helpers ─────────────────────────────────────────────────────────────

(defn- encode-stream
  "Render a sequence of delta maps as the SSE wire bytes OpenRouter would send."
  [deltas]
  (str
   (str/join "\n" (map #(str "data: " (json/write-value-as-string %)) deltas))
   "\ndata: [DONE]\n"))

(defn- drain
  "Read all values from a channel until close."
  [ch]
  (loop [acc []]
    (if-let [v (<!! ch)]
      (recur (conj acc v))
      acc)))

(defn- roundtrip [deltas]
  (drain (sse/event-stream->chan
          (ByteArrayInputStream. (.getBytes (encode-stream deltas) "UTF-8")))))

;;; ── property 1: any sequence of token strings roundtrips through SSE ─────────

(defspec sse-roundtrip-for-arbitrary-tokens 200
  (prop/for-all [tokens (gen/vector gen/string-ascii)]
                (let [deltas (mapv (fn [t] {:choices [{:delta {:content t}}]}) tokens)]
                  (= deltas (roundtrip deltas)))))

;;; ── property 2: malli-generated StreamDeltas roundtrip ───────────────────────

(defn- is-error-event? [delta]
  (or (:error delta)
      (= "error" (get-in delta [:choices 0 :finish_reason]))))

(defspec sse-roundtrip-for-malli-generated-deltas 100
  (prop/for-all [deltas (gen/vector (mg/generator schema/StreamDelta) 0 5)]
                (let [safe (vec (remove is-error-event? deltas))]
                  (= safe (roundtrip safe)))))

;;; ── property 3: config coerce! is idempotent on already-valid input ──────────

(defspec config-coerce-idempotent 50
  (prop/for-all [cfg (mg/generator schema/Config)]
                (= cfg (schema/coerce! schema/Config cfg))))

;;; ── property 4: anomalies built by error builder always validate ─────────────

(defspec error-builder-always-conforms 50
  (prop/for-all [status (gen/choose 400 599)
                 body   (gen/one-of
                         [(gen/return {})
                          (gen/return {:error {:message "boom"}})])]
                (m/validate schema/Anomaly (error/http-anomaly status body))))
