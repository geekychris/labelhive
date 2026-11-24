# LabelHive

<img src="label_hive.png" alt="LabelHive" width="180" align="right" />

**Crowd labeling. Golden truth.**

A data-labeling platform inspired by CrowdFlower / Figure Eight: jobs with
controlled gold-data injection, multi-worker consensus, gold-driven trust
scoring, auto-promotion of high-agreement labels, and templates ranging from
multi-choice to span annotation. Spring Boot backend + React frontend, with
both REST and MCP interfaces.

> **First time here?** Read [**`docs/TUTORIAL.md`**](docs/TUTORIAL.md) — an
> end-to-end walkthrough with screenshots covering admin and worker flows,
> every template type, and the quality controls.

## What's in here

```
backend/         Spring Boot 3 / Java 21 / JPA / Postgres / Flyway / Spring AI MCP server
frontend/        Vite + React + TypeScript (Tailwind, TanStack Query, React Router)
sdk/python/      Python SDK + 7 runnable examples
docs/TUTORIAL.md End-to-end walkthrough with screenshots
docs/templates/  Per-type tutorials and the custom-type SDK guide
k8s/             Kubernetes manifests (namespace, postgres, backend, frontend)
docker-compose.yml   Postgres for local dev
devportal.yaml   Asset manifest — port slots, fixtures, build/run commands
backend/Dockerfile, frontend/Dockerfile, frontend/nginx.conf
```

## Run it

```sh
# 1. Postgres (mapped to host port 5433 to avoid clashing with a system Postgres).
#    Credentials live in .env (gitignored); pick any local password.
cp .env.example .env && sed -i.bak "s/^DB_PASSWORD=.*/DB_PASSWORD=$(openssl rand -hex 16)/" .env && rm .env.bak
docker compose up -d

# 2. Backend: the `debug` profile (no sign-in, demo seeding; reads .env)
cd backend
SPRING_PROFILES_ACTIVE=debug mvn spring-boot:run

# 3. Frontend
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

In debug mode the seeder creates an admin user `demo` and three workers
(`alice`, `bob`, `carol`). The header has a debug user-switcher; the API
resolves identity from the `X-User-Id` header. The debug profile is for
local development only and refuses to start next to a deployment.

**Deployed, LabelHive signs users in with OpenID Connect** against the
site's cloud-stack login service (roles `admin` and `labeler`; env
`OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_BACKCHANNEL_URL`,
profile `deployed`). Every profile except `debug` requires it. See
[docs/AUTH.md](docs/AUTH.md) for the model, the roles and the env vars.

### Tests

```sh
cd backend
mvn test                         # 72 unit tests (no Docker needed)

# Full backend verify including the integration test (Testcontainers, or
# the docker-compose Postgres with the credentials from .env):
mvn verify
set -a; . ../.env; set +a
EXTERNAL_POSTGRES_URL=jdbc:postgresql://localhost:5433/datalabel mvn verify
# Rancher Desktop: if Testcontainers' Ryuk can't mount the socket, add
#   TESTCONTAINERS_RYUK_DISABLED=true TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

cd ../frontend
npm test                         # 20 UI tests (vitest + RTL)
```

Backend coverage:
- 6 template validators (multi-choice, free-text, image classification,
  search relevance + ordinal partial credit, span F1, bounding-box IOU).
- Service-level Mockito tests for trust quarantine, gold mixing, gold
  promotion, judgment flow, agreement aggregation, and per-query NDCG.
- MockMvc tests for the worker-facing controller (verifies the gold
  fields don't leak into the next-task / submit-judgment responses) and
  the admin gold-promotion endpoint.
- One Spring Boot integration test that runs against a real Postgres
  (Testcontainers when Docker is reachable; falls back to
  `EXTERNAL_POSTGRES_URL` for environments where Testcontainers can't
  negotiate, e.g. some Rancher Desktop setups).

Frontend coverage: each renderer (multi-choice, free-text, image
classification, search relevance, span annotation) plus the typed API
client.

## Concepts

- **Job** — a labeling task definition. Carries a `templateType` plus
  template-specific `templateConfig` (choices, label set, scale, etc.).
- **Unit** — a single thing to label. Has an optional `goldAnswer` —
  units with a gold answer are gold; ones without are unlabeled.
- **Judgment** — one worker's answer for one unit. Unique per
  (unit, user). For gold units we record whether they got it right.
- **Worker trust** — rolling per (user, job) gold accuracy. Drops below
  the job's `trustFloor` → worker is *quarantined* and the feed stops
  giving them tasks. Admin reviews disagreements via the worker page.
- **Worker instructions + exemplars** — every job has a worker-facing
  *instructions* page (`/work/{jobId}/instructions`) showing the task
  description plus a curated set of *exemplar* gold units (gold answer +
  rationale). Mark any gold unit as an exemplar by setting
  `exemplar: true` on it; admins gate which gold rows are training material.
- **Judgments per unit (fixed or escalating)** — `minJudgments` is the
  required floor; `maxJudgments` is the cap. When equal, every unit gets
  exactly that many judgments (fixed-N consensus). When `max > min`, extra
  reviewers are pulled in if the workers at the floor don't agree —
  classic "two reviewers, escalate to a third on disagreement". The cap
  prevents runaway review on contentious items.
- **Gold mixing (controlled cadence)** — every page returned to a
  worker mixes 1 gold per `goldCadence` non-gold tasks. Cadence=3 ≈ 25%
  gold; cadence=4 ≈ 20%. The worker UI never reveals which is which.
- **Gold-gating** — if a worker has no gold left to ground against,
  the feed returns *nothing* even when non-gold remains. Without gold
  we can't keep their trust score current.
- **Tasks per page** — `tasksPerPage` controls how many items the worker
  sees at once; useful for related-item tasks (e.g. 4 search-relevance
  results graded as a group). The page is shuffled so gold positions
  aren't predictable.
- **End-of-work copy** — `endOfWorkMessage` (`COME_BACK_LATER` |
  `COMPLETE`) picks the message shown when the user runs out of work.
- **Auto-promotion** — when a non-gold unit reaches enough judgments
  with high mean pairwise agreement (`promoteAgreementMin`), the
  consensus answer is promoted into a new gold unit. This grows the
  gold pool from labels we already trust.

## Built-in template types

| type                  | answer shape                                 | agreement metric        |
|-----------------------|----------------------------------------------|-------------------------|
| `MULTIPLE_CHOICE`     | `{choice}` or `{choices: []}`                | exact match             |
| `FREE_TEXT`           | `{text}`                                     | normalized exact match  |
| `IMAGE_CLASSIFICATION`| `{choice}` (payload has `imageUrl`)          | exact match             |
| `SEARCH_RELEVANCE`    | `{grade}` (per query+result pair)            | ordinal partial credit + per-query NDCG report |
| `SPAN_ANNOTATION`     | `{spans: [{start,end,label}]}`               | token-level F1          |
| `BOUNDING_BOX`        | `{boxes: [{x,y,w,h,label}]}`                 | mean best-IOU per box   |

## Building a job

Job creation is a step-by-step wizard, not a single form:

1. **Basics** — name, description, template type (picked from the catalog the
   server publishes via `/api/templates`).
2. **Variant config** — the per-type config form is rendered from each
   template's variant schema (see SDK below). For multi-choice you edit
   choices and the multi-select toggle; for search relevance you edit the
   ordinal scale (label/grade rows). Tweaking a variant — say, "7-point
   relevance scale" — does not require any Java edits.
3. **Display template** — optional Mustache-style template for rendering each
   unit's payload (`{{result.title}}`, `{{query}}`). Live preview against the
   first staged unit.
4. **Quality settings** — gold ratio, judgments per unit, trust floor,
   auto-promotion threshold.
5. **Dataset** — paste a JSON array of staged units (no commit until step 7).
6. **Gold labeling** — for any staged unit, click *label* to open the worker
   renderer inline and capture a gold answer. Mid-build labeling — no need
   to upload, then circle back to label, then come back.
7. **Review and create** — single click commits the job and uploads the
   staged units in one shot.

**Cloning a job**: every job-detail page has a *clone* button — a confirm
prompt asks whether to copy units (and whether to copy gold answers along
with them). Useful for "fresh round on the same dataset" or branching to
test a different agreement threshold.

### Adding a new template type — the SDK

The platform exposes a small SDK so new annotation types can be added without
touching anything else. Two files. See the full walkthrough in
[`docs/templates/CUSTOM_TYPE_TUTORIAL.md`](docs/templates/CUSTOM_TYPE_TUTORIAL.md).

1. **Backend** — implement `com.datalabel.sdk.LabelingTemplate`, mark it
   `@Component`. Return any string from `type()`; built-ins live in
   `com.datalabel.sdk.TemplateType` for convenience but the system stores ids
   as plain strings, so plugin JARs can register new ids without modifying
   the core. Override `variantSchema()` to declare your config knobs — the
   builder UI will render a form from it (no per-type frontend code needed
   for the admin side). The package's `package-info.java` doubles as a
   tutorial.
2. **Frontend** — drop a renderer in `frontend/src/sdk/renderers/` and
   register it via `defineRenderer({ type, render })` in
   `sdk/renderers/index.ts`.

Each built-in template's source has a tutorial-grade Javadoc covering goal,
config schema, answer schema, equality, agreement metric, aggregation, and
common pitfalls — read them in the order suggested at the top of
`com.datalabel.sdk.package-info`.

What you get for free: gold mixing, trust scoring, auto-promotion, MCP
exposure, the admin monitor, the worker feed.

## Demo datasets

When `datalabel.demo.seed-on-startup=true` (the default in the `debug`
profile) the seeder populates jobs covering each template type:

- **Search relevance — AI tooling queries** (NDCG-driven)
- **Is this image AI-generated?** (image classification, contemporary
  content-provenance task)
- **Keyword span annotation** (PRODUCT / MODEL / COMPANY mentions)
- **Sentiment on developer social posts**
- **Content moderation — safe / borderline / unsafe**
- **LLM answer factuality** (correct / partially / incorrect / unverifiable)

Reset by dropping the database and restarting; the seeder is idempotent
(no-ops if any jobs already exist).

## Deploy to Kubernetes (devportal-managed)

The repo ships with a `devportal.yaml`, matching `Dockerfile`s, and a `k8s/`
manifest set. devportal allocated a single externally-visible HTTP slot:

| Scope | Slot | Port |
|---|---|---|
| `k8s-nodeport` | `http` | **30009** — `frontend` Service NodePort |
| `local` | `http` | **18011** — for `docker run -p 18011:80 labelhive/frontend:latest` |

In-cluster the **frontend nginx** proxies `/api`, `/sse` + `/mcp` (MCP),
`/oauth2/`, `/login/oauth2/`, `/logout` (OIDC sign-in/out) and
`/actuator/health` to the internal `backend` ClusterIP service, and `backend` talks to a ClusterIP
`postgres` service. Only the frontend exposes a NodePort — so the cluster
surface is minimal and the demo URL is `http://<node-ip>:30009`.

```sh
# 1. Build images (point Docker at the cluster's image cache for kind/minikube/k3d).
docker build -t labelhive/backend:latest  -f backend/Dockerfile  backend
docker build -t labelhive/frontend:latest -f frontend/Dockerfile frontend

# 2. Secrets (not in git) and the site's OIDC settings (edit k8s/05-config.yaml).
kubectl apply -f k8s/00-namespace.yaml
kubectl -n labelhive create secret generic labelhive-db \
  --from-literal=username=datalabel --from-literal=password="$(openssl rand -hex 24)"
kubectl -n labelhive create secret generic labelhive-oidc --from-literal=client-secret='<registered secret>'

# 3. Apply manifests and wait for the backend.
kubectl apply -f k8s/
kubectl -n labelhive rollout status deploy/backend

# 4. Open the UI through the allocated NodePort (or any ingress in front of the
#    frontend service — nginx handles /api and /mcp proxying internally):
open http://$(minikube ip):30009     # or: open http://localhost:30009 with kind/k3d port-mapped
```

The backend pod runs the `deployed` profile: OIDC sign-in against the
site's login service (see [docs/AUTH.md](docs/AUTH.md)), so the client
must be registered there with the redirect URI
`https://<public name>/login/oauth2/code/cloudstack`. It no longer runs the
debug profile or the demo seeder. The lab's own deployment is cloud-stack's
`deploy/labelhive` (one NodePort, 31850, behind the site proxy at
`https://labelhive.<zone>/`).

The asset is already registered as **`labelhive`** (`owner: geekychris`) via:

```python
mcp__devportal__register_from_github(fullName="geekychris/labelhive")
mcp__devportal__allocate_ports(assetId="labelhive", scope="k8s-nodeport")
# → port 30009 — wired into k8s/30-frontend.yaml
mcp__devportal__allocate_ports(assetId="labelhive", scope="local")
# → port 18011 — for `docker run -p 18011:80 labelhive/frontend:latest`
```

To shift to a new port range later: call `allocate_ports(..., reallocate=True)`
and update the `nodePort:` value in `k8s/30-frontend.yaml`.

To consume the shared Postgres meta-asset instead of bundling one, attach
it as a runtime dependency:

```python
mcp__devportal__attach_consumes(assetId="labelhive", metaAssetId="postgres-shared", role="primary-db")
```

…and remove `k8s/10-postgres.yaml`; the backend env block already takes
`DB_HOST/DB_PORT/...` from runtime config.

## SDK + integration

A Python SDK lives at `sdk/python/`. Single file (`labelhive/client.py`)
with `httpx` as the only dependency, plus seven runnable examples covering
every common workflow:

```sh
cd sdk/python && pip install -e .
python examples/01_inspect_templates.py
```

| Example | What it shows |
|---|---|
| 01 inspect templates       | Discover types and their payload schemas |
| 02 build job               | Create a job from scratch                |
| 03 upload with validation  | Pre-validate a dataset before uploading  |
| 04 run as worker           | Pretend to be a worker; submit judgments |
| 05 pull reports            | Agreement / monitor / gold-mistake views |
| 06 export for training     | Pull every unit + judgment as JSONL      |
| 07 clone job               | All three clone modes                    |

The MCP server exposes the same surface to LLM agents (see *MCP* below).

## Dataset format and validation

Every template advertises a payload schema (description + required fields +
example unit) reachable at `/api/templates/{type}`. Use it as your dataset
contract. The Python SDK and the job-builder UI both consume it.

Run a candidate dataset through `/api/templates/{type}/validate-dataset`
before uploading — same checks the upload endpoint runs, but in a single
dry-run with per-row errors. The job builder's *Dataset* step and the
JobDetail upload section both expose this validator inline.

## REST API

```
GET    /api/me                               {username, roles, sub, name, email, id, role}; 401 when signed out
GET    /api/auth/me
GET    /api/auth/debug-users                 (debug mode only)

GET    /api/jobs
POST   /api/jobs
GET    /api/jobs/{id}
PUT    /api/jobs/{id}
DELETE /api/jobs/{id}
POST   /api/jobs/{id}/clone                  body: {includeUnits?, includeGold?}

GET    /api/jobs/{id}/units
POST   /api/jobs/{id}/units                  upload list of units (gold optional)

GET    /api/jobs/{id}/tasks/next             worker feed (mixes gold)
POST   /api/jobs/{id}/tasks/{unitId}/judgment

GET    /api/templates                        list registered template ids
GET    /api/templates/{type}                 variant + payload schemas
POST   /api/templates/{type}/validate-dataset dry-run a dataset against the template's validators

GET    /api/jobs/{id}/export                 bulk export: each unit with all of its judgments

GET    /api/jobs/{id}/reports/agreement      mean pairwise agreement, NDCG (search), trust roll-up
GET    /api/jobs/{id}/reports/monitor        live completeness + per-worker leaderboard
GET    /api/jobs/{id}/reports/worker/{userId}/gold-mistakes

POST   /api/admin/units/{unitId}/promote-to-gold        attributes the acting admin
POST   /api/admin/judgments/{judgmentId}/promote-to-gold attributes the judgment's author
POST   /api/admin/jobs/{jobId}/workers/{userId}/revert   delete a worker's labels, reopen affected units
POST   /api/admin/jobs/{jobId}/workers/{userId}/clear-quarantine
GET    /api/admin/jobs/{jobId}/workers/{userId}/judgments
```

## Admin operations

- **Monitor a job in flight** — `Jobs → <job> → monitor` shows a progress bar
  for completeness (judgments collected vs needed), per-worker judgments, gold
  accuracy, mean time per task, throughput per hour, and quarantine status.
  Auto-refreshes every 5 seconds.
- **Promote a single editor's answer to gold** — on the worker review page,
  every non-gold judgment has a *promote → gold* button. The unit's gold
  answer is the worker's exact answer; the unit gets `source = PROMOTED` and
  records `promotedByUserId` / `promotedFromJudgmentId` so the admin UI shows
  *gold by alice* on the unit list.
- **Revert a worker's data** — when a worker turns out to be unreliable
  (caught either by trust quarantine or by manual review), a *revert* button
  on the worker review page deletes every judgment they submitted on the job,
  decrements the affected units' counts, and reopens any unit that was
  completed but now falls below `judgmentsPerUnit`. Their trust row is
  dropped too. The action is irreversible — confirmation required.
- **Auto-promote consensus answers** — set `autoPromoteToGold=true` and
  `promoteAgreementMin` on the job. When pairwise agreement clears the
  threshold, the consensus is promoted as gold and attributed to the first
  contributing worker whose answer matched.

## Email

LabelHive can send two emails. Both are off until a mail server is set.

- **Job invites.** On a job's page, **Invite a worker → Or email the link** sends someone the
  job's worker link and its instructions link, with an optional note (`POST /api/jobs/{id}/invite`
  `{to, note}`; admins only; only an active job). An invite doesn't grant access: the person signs
  in with the site's sign-in, and an admin gives them LabelHive (labeler) on the site's Access page.
- **Job finished.** When a job's last open non-gold unit gets its judgments, `notify-to` hears about
  it, with links to the reports and the export.

Settings (`datalabel.mail.*` in `application.yml`, or the environment):

| Variable | Meaning |
|---|---|
| `LABELHIVE_MAIL_HOST`, `LABELHIVE_MAIL_PORT`, `LABELHIVE_MAIL_TLS` | the SMTP server; TLS `starttls` (default, required, certificate checked), `tls`, or `none` (a local catcher such as Mailpit, without an account) |
| `LABELHIVE_MAIL_USERNAME` | the account, if the server wants one; its password is read from `LABELHIVE_MAIL_PASSWORD_FILE` for each send (default `/var/run/labelhive/smtp/password`) |
| `LABELHIVE_MAIL_FROM`, `LABELHIVE_MAIL_FROM_NAME`, `LABELHIVE_MAIL_REPLY_TO` | the sender (name defaults to "LabelHive") |
| `LABELHIVE_PUBLIC_URL` | the app's public address, for the links (required) |
| `LABELHIVE_SITE_NAME` | shown in subjects ("Belmont") |
| `LABELHIVE_MAIL_NOTIFY_TO` | one address for job-finished notices; empty: none |

`GET /api/mail/status` says whether mail is on and, if not, why. Sends are limited to 30 an hour
and 5 to one address. Every header value is refused if it holds a line break, a recipient must be
one plain address, and job names in subjects lose control characters. In the home lab the sites
send through Google Workspace's SMTP relay without an account (cloud-stack `docs/EMAIL.md`).

## MCP interface

The backend exposes a Model Context Protocol server (Spring AI WebMVC
transport). Tools registered:

- `listJobs`, `getJob`, `listUnits`
- `getNextTask(jobId, username)`
- `submitJudgment(unitId, username, answer, timeMs)`
- `getAgreementReport(jobId)`
- `getWorkerGoldMistakes(jobId, username)`

Wire an MCP-aware agent (Claude Desktop, Claude Code, etc.) at
`http://localhost:8080/mcp` (transport details in the Spring AI MCP
server starter docs). Use this when you want an LLM to *be* a labeler —
or to audit / report against the platform.

## Future work

- Token auth for the Python SDK and MCP against a deployed (OIDC) instance;
  today they need a browser session (docs/AUTH.md).
- Bounding-box drawing UI (canvas-based).
- Worker payouts / SLA tracking.
- Krippendorff's α as an alternative agreement statistic.
- CSV upload for units (the backend uses `opencsv`; wire the endpoint).
