# LabelHive — run

## Local (docker-compose Postgres + dev servers)

```sh
docker compose up -d
cd backend && SPRING_PROFILES_ACTIVE=debug DB_PORT=5433 mvn spring-boot:run &
cd frontend && npm run dev   # → http://localhost:5173
```

## Kubernetes (devportal-managed)

```sh
docker build -t labelhive/backend:latest  -f backend/Dockerfile  backend
docker build -t labelhive/frontend:latest -f frontend/Dockerfile frontend
kubectl apply -f k8s/
kubectl -n labelhive rollout status deploy/backend
```

Frontend is reachable on the **devportal-allocated NodePort 30009**:

```sh
open http://$(minikube ip):30009     # or http://localhost:30009 with kind/k3d port-mapping
```

## Demo-seed fixture

The backend Deployment ships with `DATALABEL_DEMO_SEED_ON_STARTUP=true`
(see `k8s/20-backend.yaml`). On a fresh cluster the seeder lays down:

- **4 users** — `demo` (admin), plus workers `alice`, `bob`, `carol`.
- **6 jobs** — search relevance (NDCG), AI/real image classification,
  keyword spans, dev sentiment, content moderation, LLM factuality.

The seeder is idempotent — re-applies are safe.

## Auth

The debug profile resolves identity from an `X-User-Id` header (numeric
id or username). Switch identity in the UI via the header dropdown, or
set the header explicitly when calling the API directly.

```sh
curl -s -H 'X-User-Id: alice' http://localhost:30009/api/jobs
```
