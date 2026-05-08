# LabelHive — architecture

## Topology

```
                       ┌────────────────────────┐
   browser ──http──►   │ frontend (nginx)       │
                       │   /static  → /app/dist │
                       │   /api/*   → backend    │
                       │   /mcp     → backend    │
                       └────────────┬───────────┘
                                    │ ClusterIP
                                    ▼
                       ┌────────────────────────┐
                       │ backend (Spring Boot)  │
                       │   • REST API           │
                       │   • Spring AI MCP      │
                       │   • Flyway migrations  │
                       └────────────┬───────────┘
                                    │ JDBC
                                    ▼
                       ┌────────────────────────┐
                       │ postgres (16-alpine)   │
                       └────────────────────────┘
```

Only the **frontend** Service exposes a NodePort (devportal slot
`http`, allocated `30009`). Backend and Postgres are ClusterIP — the
frontend nginx reverse-proxies `/api` (REST) and `/mcp` (SSE for the MCP
server) to the backend Service.

## Data model

Six core entities — `app_user`, `job`, `unit`, `judgment`, `worker_trust`,
`unit_concern`. See the JPA classes in
`backend/src/main/java/com/datalabel/domain/`.

## Templates / SDK

Annotation types are pluggable via `LabelingTemplate`
(`backend/src/main/java/com/datalabel/sdk/`). Each declares a variant
schema (config knobs) and a payload schema (the per-unit JSON contract).
The frontend mirrors this with the renderer registry under
`frontend/src/sdk/renderers/`.

A Python SDK at `sdk/python/` wraps the REST surface for headless
consumers; an MCP server (Spring AI) surfaces the same API to LLM
agents.

See `README.md` for the long-form story.
