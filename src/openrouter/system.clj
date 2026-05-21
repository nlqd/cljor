(ns openrouter.system
  "Stuart Sierra-style Component system.

   Mental model in three sentences:

   1. A `component` is a value that knows how to `start` itself and how to
      `stop` itself. We declare this contract by implementing the
      `component/Lifecycle` protocol on a defrecord.

   2. A `system` is just a map of named components. When you call
      `(component/start sys)`, Stuart's library walks the map in dependency
      order, calls `(start c)` on each component, and returns a new map
      where each component is now in its started state.

   3. Dependencies are declared with `component/using`. At start time the
      library assoc's the dependency's started value onto the dependent
      component, so by the time `(start web)` runs, `web` already has the
      started `client` accessible under `::client`.

   Why defrecord? Records can implement protocols, which is what
   `Lifecycle` is. The fields (`port`, `model`, `opts`) are bare because
   defrecord field symbols cannot be namespaced; everything else (system
   keys, assoc'd extras) is namespaced."
  (:require [com.stuartsierra.component :as component]
            [openrouter.client :as openrouter-client]
            [openrouter.client :as-alias client]
            [openrouter.config :as-alias config]
            [openrouter.web :as web]
            [ring.adapter.jetty :as jetty]))

;; ── OpenRouterClient ──────────────────────────────────────────────────────────
;;
;; Holds the validated config map and the long-lived hato HTTP client.
;; `start` builds them; `stop` clears them. The Lifecycle methods MUST return
;; the (possibly updated) component value — Stuart wires the returned value
;; into the started system map.

(defrecord OpenRouterClient [opts]
  component/Lifecycle
  (start [this]
    (let [{::client/keys [config http-client]} (openrouter-client/make-client opts)]
      (assoc this
             ::client/config      config
             ::client/http-client http-client)))
  (stop [this]
    (assoc this
           ::client/config      nil
           ::client/http-client nil)))

;; ── WebServer ─────────────────────────────────────────────────────────────────
;;
;; Holds the Jetty server. Depends on the OpenRouterClient — declared via
;; `component/using` below. At start time, Stuart assoc's the started
;; client onto `this` under the key `::client`, so we can destructure it.

(defrecord WebServer [port model jetty]
  component/Lifecycle
  (start [this]
    (let [{::keys [client]} this
          handler (web/make-handler {::web/client client ::web/model model})
          server  (jetty/run-jetty handler {:port port :join? false})]
      (assoc this :jetty server)))
  (stop [this]
    (when jetty (.stop jetty))
    (assoc this :jetty nil)))

;; ── the system map ────────────────────────────────────────────────────────────
;;
;; Constructs but does NOT start the system. Callers do `(component/start sys)`
;; to bring it up and `(component/stop started)` to bring it down.
;;
;; `component/using` says "when starting this WebServer, first ensure
;; ::client is started, then assoc its value onto me under the key ::client".

(defn system
  "Construct (but do not start) the system.

   Required:    ::config/api-key
   Config opts: ::config/base-url, ::config/http-referer, ::config/x-title
   Web opts:    ::web/port (default 3000), ::web/model (default openai/gpt-4o-mini)"
  [{::config/keys [api-key base-url http-referer x-title]
    ::web/keys    [port model]
    :or {model "openai/gpt-4o-mini"
         port  3000}}]
  (component/system-map
   ::client
   (map->OpenRouterClient
    {:opts (cond-> {::config/api-key api-key}
             base-url     (assoc ::config/base-url     base-url)
             http-referer (assoc ::config/http-referer http-referer)
             x-title      (assoc ::config/x-title      x-title))})

   ::web
   (component/using
    (map->WebServer {:port port :model model})
    [::client])))
