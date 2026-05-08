# Example: registering LabelHive with devportal and rolling it out to Kubernetes

This is a transcript of the actual MCP calls that took LabelHive from
"freshly written code" to "running on a single-node k3s cluster with
demo data seeded." Every input/output is verbatim — copy-paste a similar
sequence to onboard a new asset.

The asset id is `labelhive` (`owner: geekychris`,
`https://github.com/geekychris/labelhive`). The portal allocates a
single externally-visible HTTP slot; the in-cluster topology has the
frontend nginx proxying `/api` and `/mcp` to a ClusterIP backend, which
in turn talks to a ClusterIP Postgres.

---

## 0. Prerequisites

- The repo exists on GitHub with `devportal.yaml`, Dockerfiles, and a
  `k8s/` manifest directory at HEAD.
- Local Docker can build images visible to the cluster (Rancher Desktop
  / kind / k3d / minikube — anything where `imagePullPolicy: Never`
  resolves the local image).
- A working `kubectl config current-context` pointing at the target
  cluster.

```sh
git remote -v
# origin  git@github.com:geekychris/labelhive.git (fetch)
kubectl config current-context
# rancher-desktop
```

## 1. Register the asset

`register_from_github` reads `devportal.yaml` at HEAD and seeds the
asset record (id, name, description, owner, k8sNamespace, tags).

```python
mcp__devportal__register_from_github(fullName="geekychris/labelhive")
```

```json
{
  "id": "labelhive",
  "name": "labelhive",
  "owner": "geekychris",
  "type": "library",
  "repoUrl": "https://github.com/geekychris/labelhive",
  "repoDefaultBranch": "main",
  "lifecycle": "experimental",
  "k8sNamespace": "labelhive",
  "createdAt": "2026-05-08T06:04:28.434391Z"
}
```

> Re-running `register_from_github` returns `409 Conflict` —
> registration is one-shot per asset id.

## 2. Allocate ports

```python
mcp__devportal__allocate_ports(assetId="labelhive", scope="k8s-nodeport")
mcp__devportal__allocate_ports(assetId="labelhive", scope="local")
```

```json
[{ "id": 26, "assetId": "labelhive", "slotName": "http",
   "scope": "k8s-nodeport", "port": 30009, "protocol": "tcp" }]
```

```json
[{ "id": 27, "assetId": "labelhive", "slotName": "http",
   "scope": "local",        "port": 18011, "protocol": "tcp" }]
```

The portal handed back **30009** for the in-cluster NodePort and **18011**
for "if you `docker run` the production frontend image locally." Both go
into one slot called `http`. Wire the NodePort into `k8s/30-frontend.yaml`:

```yaml
spec:
  type: NodePort
  ports:
    - name: http
      port: 80
      targetPort: 80
      nodePort: 30009     # ← from devportal
```

For a different port range later: pass `reallocate=True` and update
the manifest with the new value.

## 3. Audit + fix the manifest

```python
mcp__devportal__audit_asset(assetId="labelhive")
```

First pass:

```json
{
  "errors": 1, "warnings": 3, "info": 1,
  "findings": [
    { "code": "manifest.invalid", "severity": "error",
      "message": "devportal.yaml fails schema validation: 7 issue(s).",
      "fixHint": "$.spec.runtime.ports[0]: property 'description' is not defined ..." },
    { "code": "docs.missing.docs/architecture.md", "severity": "warn", ... },
    { "code": "docs.missing.docs/build.md",        "severity": "warn", ... },
    { "code": "docs.missing.docs/run.md",          "severity": "warn", ... },
    { "code": "frontend.detected", "severity": "info",
      "message": "VITE tier at frontend (npm, out=dist) — Dockerfile present." }
  ]
}
```

Two fixes locally:

1. Remove the non-schema `description:` field from each
   `spec.runtime.ports[]` entry in `devportal.yaml`.
2. Create the doc skeletons listed under `spec.docs.*`:
   `docs/ARCHITECTURE.md`, `docs/BUILD.md`, `docs/RUN.md`.

Commit + push to `origin/main`.

## 4. Refresh the portal's workspace clone

The portal keeps its own clone under `~/.devportal/workspace/<assetId>`.
After a push, check what the portal sees:

```python
mcp__devportal__workspace_status(assetId="labelhive")
```

```json
{ "branch": "main", "head": "6dc2ba17fc08...", "aheadCount": 0, "behindCount": 0,
  "clean": true, "files": [] }
```

If `head` is behind `origin/main`, kick a build — the portal fetches
fresh as part of build prep:

```python
mcp__devportal__kick_build(assetId="labelhive", commandName="build", ref="main")
```

```json
{
  "id": 276, "assetId": "labelhive",
  "commandName": "build",
  "commandLine": "cd backend && mvn -DskipTests package",
  "gitRef": "main", "status": "queued",
  "workspacePath": "/Users/chris/.devportal/workspace/labelhive",
  "logPath": "/Users/chris/.devportal/logs/labelhive-1778220701614.log"
}
```

Verify the workspace caught up:

```python
mcp__devportal__workspace_status(assetId="labelhive")
# → "head": "b30fb3f..."  ← latest commit
```

```python
mcp__devportal__list_builds(assetId="labelhive")
# → status: "succeeded", exitCode: 0
```

Re-audit:

```python
mcp__devportal__audit_asset(assetId="labelhive")
```

```json
{ "errors": 0, "warnings": 0, "info": 1,
  "findings": [{ "code": "frontend.detected", ... }] }
```

Clean.

## 5. Build the container images

```sh
docker build -t labelhive/backend:latest  -f backend/Dockerfile  backend
docker build -t labelhive/frontend:latest -f frontend/Dockerfile frontend
```

The Dockerfiles are multi-stage:

- **backend** — `maven:3.9-eclipse-temurin-21` builder → `eclipse-temurin:21-jre`
  runtime; the fat-jar lands at `/app/app.jar`.
- **frontend** — `node:23-alpine` Vite build → `nginx:1.27-alpine`
  serving `/usr/share/nginx/html`, with `/api/*` and `/mcp` proxied to
  the in-cluster backend service.

## 6. Apply the k8s manifests

```sh
kubectl apply -f k8s/
```

```
namespace/labelhive created
secret/postgres-credentials created
deployment.apps/postgres created
service/postgres created
deployment.apps/backend created
service/backend created
deployment.apps/frontend created
service/frontend created
```

Wait for rollouts:

```sh
kubectl -n labelhive rollout status deploy/postgres
kubectl -n labelhive rollout status deploy/backend
kubectl -n labelhive rollout status deploy/frontend
kubectl -n labelhive get pods
```

```
NAME                        READY   STATUS    RESTARTS      AGE
backend-7966ff7cfb-9n29p    1/1     Running   2 (49s ago)   56s
frontend-5db858887d-pjqbj   1/1     Running   0             56s
postgres-57c4c5678d-mrbr2   1/1     Running   0             56s
```

> Backend "RESTARTS 2" is normal on first boot: Spring's first health
> probe attempt happens before Flyway finishes migrating — the next
> readiness check passes.

## 7. Smoke-test through the cluster

The frontend is on `NodePort 30009`. The backend is ClusterIP — exposed
only through nginx (or via `port-forward` for direct API debugging).

```sh
# Demo seeder ran? (4 users + 6 jobs = 7 with bbox added)
kubectl -n labelhive port-forward svc/backend 8081:8080 &
curl -s -H 'X-User-Id: demo' http://localhost:8081/api/jobs | jq -r '.[] | "#\(.id) \(.name)"'
# #1 Search relevance — AI tooling queries
# #2 Is this image AI-generated?
# #3 Find the dogs
# #4 Keyword span annotation
# #5 Sentiment on developer social posts
# #6 Content moderation — safe / borderline / unsafe
# #7 LLM answer factuality

# UI reachable on the allocated NodePort
curl -sI http://localhost:30009/
# HTTP/1.1 200 OK
# Server: nginx/1.27.5
```

Open `http://localhost:30009/` (or `http://$(minikube ip):30009/`).

## 8. Snapshot portal state

```python
mcp__devportal__state_git_sync(message="labelhive: registered, ports allocated, deployed to k3s")
```

```json
{ "dir": "/Users/chris/.devportal/state", "initialized": false,
  "commit": "0182956a7a0285905067eea0a9c6600ac969830d" }
```

The portal's state repo now has a snapshot tagged with the message.

---

## Quick reference

| Step | MCP call | Output |
|---|---|---|
| Register | `register_from_github(fullName="geekychris/labelhive")` | asset id `labelhive` |
| Allocate (k8s) | `allocate_ports(assetId, scope="k8s-nodeport")` | NodePort **30009** |
| Allocate (local) | `allocate_ports(assetId, scope="local")` | local **18011** |
| Audit | `audit_asset(assetId)` | flagged schema + missing docs |
| Refresh + build | `kick_build(assetId, ref="main")` | build #276 succeeded |
| Re-audit | `audit_asset(assetId)` | 0 errors |
| Snapshot | `state_git_sync()` | commit `0182956…` |

| Step | Shell command |
|---|---|
| Build images | `docker build -t labelhive/{backend,frontend}:latest …` |
| Apply | `kubectl apply -f k8s/` |
| Wait | `kubectl -n labelhive rollout status deploy/{postgres,backend,frontend}` |
| Verify | `curl http://localhost:30009/` |
