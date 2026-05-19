(ns openrouter.system
  "Stuart Sierra-style Component system. Each stateful resource gets a record
   with start/stop; the system-map wires them together by dependency.

   Data convention:
     - Record fields (`port`, `model`, `jetty`, `opts`) are bare — Clojure's
       defrecord cannot have namespaced field symbols.
     - Everything else (system-map keys, assoc'd extras, opts entries) is
       namespaced."
  (:require [com.stuartsierra.component :as component]
            [openrouter.core :as or-client]
            [openrouter.web :as web]
            [ring.adapter.jetty :as jetty]))

(defrecord OpenRouterClient [opts]
  component/Lifecycle
  (start [this]
    (let [{:openrouter.client/keys [config http-client]} (or-client/make-client opts)]
      (assoc this
             :openrouter.client/config      config
             :openrouter.client/http-client http-client)))
  (stop [this]
    (assoc this
           :openrouter.client/config      nil
           :openrouter.client/http-client nil)))

(defrecord WebServer [port model jetty]
  component/Lifecycle
  (start [this]
    (let [{:openrouter.system/keys [client]} this
          handler (web/make-handler {:openrouter.web/client client
                                     :openrouter.web/model  model})
          server  (jetty/run-jetty handler {:port port :join? false})]
      (assoc this :jetty server)))
  (stop [this]
    (when jetty (.stop jetty))
    (assoc this :jetty nil)))

(defn system
  "Construct (but do not start) the system.

   Required: :openrouter.config/api-key
   Optional config: :openrouter.config/base-url, /http-referer, /x-title
   Web:             :openrouter.web/port (default 3000)
                    :openrouter.web/model (default openai/gpt-4o-mini)"
  [{:openrouter.config/keys [api-key base-url http-referer x-title]
    :openrouter.web/keys    [port model]
    :or {model "openai/gpt-4o-mini"
         port  3000}}]
  (component/system-map
   :openrouter.system/client
   (map->OpenRouterClient
    {:opts (cond-> {:openrouter.config/api-key api-key}
             base-url     (assoc :openrouter.config/base-url     base-url)
             http-referer (assoc :openrouter.config/http-referer http-referer)
             x-title      (assoc :openrouter.config/x-title      x-title))})

   :openrouter.system/web
   (component/using
    (map->WebServer {:port port :model model})
    [:openrouter.system/client])))
