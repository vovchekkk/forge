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

## Pipeline DSL

```yaml
name: build-and-test
jobs:
  - name: build
    image: maven:3.9-eclipse-temurin-25
    script:
      - mvn -q clean package
  - name: test
    image: maven:3.9-eclipse-temurin-25
    depends_on:
      - build
    script:
      - mvn -q test
```

Job execution order is derived from the `depends_on` graph (DAG) on the server
side. Runs are driven by a state machine (`PENDING → QUEUED → RUNNING → …`).

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