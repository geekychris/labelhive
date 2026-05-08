# LabelHive — build

## Prerequisites
- Java 21
- Maven 3.9+
- Node 23 / npm 11
- Docker (for Postgres + image builds)

## Local build

```sh
# Backend (compile + tests)
cd backend
mvn test

# Frontend (typecheck + tests)
cd ../frontend
npm install
npm test

# Python SDK (editable install)
cd ../sdk/python
pip install -e .
```

## Container images

```sh
docker build -t labelhive/backend:latest  -f backend/Dockerfile  backend
docker build -t labelhive/frontend:latest -f frontend/Dockerfile frontend
```

## Devportal builds

```python
mcp__devportal__kick_build(assetId="labelhive", commandName="build")
mcp__devportal__list_builds(assetId="labelhive")
mcp__devportal__get_build_log(buildId=<id>)
```
