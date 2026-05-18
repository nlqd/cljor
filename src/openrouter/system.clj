(ns openrouter.system
  "Stuart Sierra–style Component system. Each stateful resource gets a record
   with start/stop; the system-map wires them together by dependency."
  (:require [com.stuartsierra.component :as component]
            [openrouter.core :as or-client]
            [openrouter.web :as web]
            [ring.adapter.jetty :as jetty]))

(defrecord OpenRouterClient [opts]
  component/Lifecycle
  (start [this]
    (let [{:keys [config http-client]} (or-client/make-client opts)]
      (assoc this :config config :http-client http-client)))
  (stop [this]
    (assoc this :config nil :http-client nil)))

(defrecord WebServer [port model jetty client]
  component/Lifecycle
  (start [this]
    (let [handler (web/make-handler {:client client :model model})
          server  (jetty/run-jetty handler {:port port :join? false})]
      (assoc this :jetty server)))
  (stop [this]
    (when jetty (.stop jetty))
    (assoc this :jetty nil)))

(defn system
  "Construct (but do not start) the system. Pass :api-key (required) plus any
   optional :base-url :http-referer :x-title :model :port."
  [{:keys [api-key base-url http-referer x-title model port]
    :or   {model "openai/gpt-4o-mini"
           port  3000}}]
  (component/system-map
   :client (map->OpenRouterClient
            {:opts (cond-> {:api-key api-key}
                     base-url     (assoc :base-url base-url)
                     http-referer (assoc :http-referer http-referer)
                     x-title      (assoc :x-title x-title))})
   :web    (component/using
            (map->WebServer {:port port :model model})
            [:client])))
