# openrouter-clj

Minimal Clojure client and chat UI for the OpenRouter API.

## Setup

Put your key in `.env`:

```
OPENROUTER_API_KEY=sk-or-...
OPENROUTER_MODEL=openai/gpt-4o-mini   # optional, defaults to gpt-4o-mini
```

Load it however you like (`direnv`, `set -a; source .env; set +a`, etc.) before running anything below.

## Run the chat UI

```
clojure -M:serve
```

Then open http://localhost:3000.

## Run tests

```
clojure -X:test
```

## REPL (reloaded workflow)

```
clojure -M:dev
```

Drops into a REPL with `dev/user.clj` loaded:

```clojure
(go)     ; build and start the Component system
(stop)   ; shut it down
(reset)  ; stop, reload changed namespaces, restart
```

## Library use

```clojure
(require '[openrouter.core :as or])

(def client (or/make-client {:api-key (System/getenv "OPENROUTER_API_KEY")}))

(or/complete client {:model    "openai/gpt-4o-mini"
                     :messages [{:role "user" :content "Hello"}]})
```

For streaming, `or/complete-stream` returns a `core.async` channel of delta maps; it closes after the last token.
