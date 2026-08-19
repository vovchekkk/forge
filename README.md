# forge

A small self-hosted CI/CD system for running Dockerized pipelines.

`forge` lets you describe pipelines in a simple YAML DSL, schedule them on a
server, and execute them on remote runners that poll for jobs, check out the
repository and run each job step in a container.

## Modules

| Module          | Description                                                              |
|-----------------|--------------------------------------------------------------------------|
| `shared`        | Pipeline model, YAML parser, validator and the server/runner protocol DTOs |
| `forge-server`  | Spring Boot server: projects, pipelines, runs, runner registry, scheduler |
| `forge-runner`  | Spring Boot agent: polls the server, clones repos, executes jobs in Docker |
| `forge-e2e`     | End-to-end tests: full pipeline against real Postgres, Docker and a runner |

## Pipeline DSL

```yaml
name: build-and-test
image: maven:3.9-eclipse-temurin-25
jobs:
  build:
    commands:
      - mvn -q clean package
  test:
    needs: [build]
    commands:
      - mvn -q test
```

Job execution order is derived from the `needs` graph (DAG) on the server
side. Each job can also set `timeout` (seconds) and `environment` (map of
env vars). Runs are driven by a state machine
(`PENDING → QUEUED → RUNNING → SUCCESS/FAILED/CANCELED`).

## Getting started

Requirements: JDK 25, Maven 3.9+, Docker (for runners), PostgreSQL (for the server).

```bash
# 1. Start PostgreSQL and point forge-server at it
#    (DB_URL/DB_USER/DB_PASSWORD, defaults: jdbc:postgresql://localhost:5432/forge)

# 2. Start the server
mvn -pl forge-server spring-boot:run

# 3. Register a runner
mvn -pl forge-runner spring-boot:run \
  -Dspring-boot.run.arguments="--forge.server.token=local-dev-token --forge.runner.name=local-runner"
```

Interactive API docs are available at `http://localhost:8080/swagger-ui.html`.

## Testing

```bash
mvn -pl shared test
mvn -pl forge-server test   # integration tests spin up PostgreSQL via Testcontainers
```

## Deployment

See [docs/deploy.md](docs/deploy.md) for a step-by-step guide to deploying a
self-hosted instance with Docker Compose and HTTPS (Caddy): domain purchase,
VPS selection, Docker setup, and end-to-end verification.