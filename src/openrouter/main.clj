(ns openrouter.main
  (:require [com.stuartsierra.component :as component]
            [openrouter.system :as system])
  (:gen-class))

(defn -main [& _args]
  (let [api-key (System/getenv "OPENROUTER_API_KEY")]
    (when-not api-key
      (throw (ex-info "OPENROUTER_API_KEY env var is required" {}))))
  (let [sys (component/start
             (system/system
              (cond-> {:openrouter.config/api-key      (System/getenv "OPENROUTER_API_KEY")
                       :openrouter.config/http-referer "http://localhost:3000"
                       :openrouter.config/x-title      "OpenRouter Chat Demo"
                       :openrouter.web/port            3000}
                (System/getenv "OPENROUTER_MODEL")
                (assoc :openrouter.web/model (System/getenv "OPENROUTER_MODEL")))))]
    (println "Listening on http://localhost:3000")
    (.join (-> sys :openrouter.system/web :jetty))))
