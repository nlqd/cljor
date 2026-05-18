(ns openrouter.main
  (:require [com.stuartsierra.component :as component]
            [openrouter.system :as system])
  (:gen-class))

(defn -main [& _args]
  (let [api-key (System/getenv "OPENROUTER_API_KEY")]
    (when-not api-key
      (throw (ex-info "OPENROUTER_API_KEY env var is required" {}))))
  (let [sys (component/start
             (system/system {:api-key      (System/getenv "OPENROUTER_API_KEY")
                             :http-referer "http://localhost:3000"
                             :x-title      "OpenRouter Chat Demo"
                             :model        (System/getenv "OPENROUTER_MODEL")
                             :port         3000}))]
    (println "Listening on http://localhost:3000")
    (.join (-> sys :web :jetty))))
