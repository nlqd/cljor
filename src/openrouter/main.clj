(ns openrouter.main
  (:require [com.stuartsierra.component :as component]
            [openrouter.config :as-alias config]
            [openrouter.system :as system]
            [openrouter.web :as-alias web])
  (:gen-class))

(defn -main [& _args]
  (let [api-key (System/getenv "OPENROUTER_API_KEY")]
    (when-not api-key
      (throw (ex-info "OPENROUTER_API_KEY env var is required" {}))))
  (let [sys (component/start
             (system/system
              (cond-> {::config/api-key      (System/getenv "OPENROUTER_API_KEY")
                       ::config/http-referer "http://localhost:3000"
                       ::config/x-title      "OpenRouter Chat Demo"
                       ::web/port            3000}
                (System/getenv "OPENROUTER_MODEL")
                (assoc ::web/model (System/getenv "OPENROUTER_MODEL")))))]
    (println "Listening on http://localhost:3000")
    (.join (-> sys ::system/web :jetty))))
