(ns user
  "Reloaded workflow entry point. Start a REPL with `clojure -M:dev`, then:
     (go)     - construct and start the system
     (stop)   - shut it down
     (reset)  - stop, reload changed namespaces, restart"
  (:require [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [openrouter.config :as-alias config]
            [openrouter.system :as openrouter]
            [openrouter.web :as-alias web]))

(repl/set-refresh-dirs "src" "dev" "test")

(def ^:private config
  {::config/api-key      (or (System/getenv "OPENROUTER_API_KEY") "sk-dev")
   ::config/http-referer "http://localhost:3000"
   ::config/x-title      "OpenRouter Chat Dev"
   ::web/model           (or (System/getenv "OPENROUTER_MODEL") "openai/gpt-4o-mini")
   ::web/port            3000})

(def system nil)

(defn init []
  (alter-var-root #'system (constantly (openrouter/system config))))

(defn start []
  (alter-var-root #'system component/start)
  :started)

(defn stop []
  (alter-var-root #'system #(when % (component/stop %)))
  :stopped)

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (repl/refresh :after 'user/go))
