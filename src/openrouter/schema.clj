(ns openrouter.schema
  "Single source of truth for the data that crosses module boundaries.

   Naming convention:
     - Keys we own        →  namespaced (`:openrouter.config/api-key`).
     - Keys we mirror from
       the OpenRouter wire →  bare (`:model`, `:messages`, `:content`).
       Renaming OpenRouter's vocabulary inside our walls would hide that
       we're talking to them.

   Schemas are plain Malli data values; nothing is registered globally."
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]))

;;; ── data we own ──────────────────────────────────────────────────────────────

(def Config
  "Validated configuration produced by `openrouter.config/make-client`."
  [:map {:closed true}
   [:openrouter.config/api-key      :string]
   [:openrouter.config/base-url     {:default "https://openrouter.ai/api/v1"} :string]
   [:openrouter.config/timeout-ms   {:default 30000} :int]
   [:openrouter.config/http-referer {:optional true} :string]
   [:openrouter.config/x-title      {:optional true} :string]])

(def Anomaly
  "Structured error payload. Follows Cognitect's anomalies convention plus
   our own openrouter.anomaly/* extras for HTTP context."
  [:map {:closed true}
   [:cognitect.anomalies/category
    [:enum :unavailable :busy :not-found :forbidden
     :fault :incorrect :interrupted :unsupported]]
   [:cognitect.anomalies/message {:optional true} :string]
   [:openrouter.anomaly/status   {:optional true} :int]
   [:openrouter.anomaly/body     {:optional true} :any]
   [:openrouter.anomaly/cause    {:optional true} :any]])

(def Client
  "A live OpenRouter client value: validated config plus the hato HttpClient
   that issues requests. Constructed by `openrouter.client/make-client`."
  [:map {:closed true}
   [:openrouter.client/config      Config]
   [:openrouter.client/http-client [:fn {:error/message "must be non-nil"} some?]]])

(def RequestEnvelope
  "Data-first request value handed to `openrouter.request/execute!`.

   The shape is a thin mirror over hato's request map; we use bare keys
   because renaming hato's vocabulary inside our walls would buy no
   information. `:path` (not `:url`) and `:stream?` are ours; everything
   else flows straight through to hato."
  [:map {:closed true}
   [:method  [:enum :get :post]]
   [:path    :string]
   [:body    {:optional true} [:maybe :map]]
   [:stream? {:default false} :boolean]])

;;; ── data we mirror from OpenRouter ───────────────────────────────────────────

(def Message
  [:map {:closed true}
   [:role    [:enum "system" "user" "assistant"]]
   [:content :string]])

(def ChatRequest
  [:map
   [:model    :string]
   [:messages [:vector Message]]
   [:stream   {:optional true} :boolean]])

(def StreamDelta
  "A single SSE chunk decoded from `data: {...}`."
  [:map
   [:choices
    [:vector
     [:map
      [:delta         [:map [:content {:optional true} [:maybe :string]]]]
      [:finish_reason {:optional true} [:maybe :string]]]]]])

;;; ── helpers ──────────────────────────────────────────────────────────────────

(defn explain
  "Human-readable explanation of why `value` does not conform to `schema`, or
   nil if it does."
  [schema value]
  (some-> (m/explain schema value) me/humanize))

(defn coerce!
  "Decode + validate `value` against `schema`. Applies defaults via
   `mt/default-value-transformer`. Throws an ex-info whose ex-data is itself
   an Anomaly when validation fails."
  [schema value]
  (let [decoded (m/decode schema value (mt/default-value-transformer))]
    (if (m/validate schema decoded)
      decoded
      (throw
       (ex-info "schema validation failed"
                {:cognitect.anomalies/category :incorrect
                 :cognitect.anomalies/message  (pr-str (explain schema decoded))
                 :openrouter.anomaly/body      value})))))
