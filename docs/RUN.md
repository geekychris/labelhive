# LabelHive — run

## Local (docker-compose Postgres + dev servers)

```sh
cp .env.example .env          # set DB_PASSWORD (any local value)
docker compose up -d
cd backend && SPRING_PROFILES_ACTIVE=debug mvn spring-boot:run &   # reads ../.env
cd frontend && npm run dev   # → http://localhost:5173
```

## Kubernetes (devportal-managed)

```sh
docker build -t labelhive/backend:latest  -f backend/Dockerfile  backend
docker build -t labelhive/frontend:latest -f frontend/Dockerfile frontend
# once: the Secrets labelhive-db and labelhive-oidc (see k8s/05-config.yaml)
kubectl apply -f k8s/
kubectl -n labelhive rollout status deploy/backend
```

The pod runs the `deployed` profile (OIDC sign-in; docs/AUTH.md). The lab
deployment is cloud-stack's `deploy/labelhive` (NodePort 31850).

Frontend is reachable on the **devportal-allocated NodePort 30009**:

```sh
open http://$(minikube ip):30009     # or http://localhost:30009 with kind/k3d port-mapping
```

## Demo-seed fixture

The `debug` profile (local development) seeds on startup:

- **4 users** — `demo` (admin), plus workers `alice`, `bob`, `carol`.
- **6 jobs** — search relevance (NDCG), AI/real image classification,
  keyword spans, dev sentiment, content moderation, LLM factuality.

The seeder is idempotent — re-applies are safe.

## Auth

Deployed: OpenID Connect against the site's login service; see
[AUTH.md](AUTH.md) (env vars, roles, `GET /api/me`).

Local `debug` profile: identity from an `X-User-Id` header (numeric id or
username). Switch identity in the UI via the header dropdown, or set the
header explicitly when calling the API directly. The debug profile refuses
to start when `OIDC_ISSUER` is set or the `deployed`/`prod` profile is active.

```sh
curl -s -H 'X-User-Id: alice' http://localhost:8080/api/jobs
```
