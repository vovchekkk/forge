# Deploying Forge

This guide walks through deploying a self-hosted Forge CI instance: a VPS
running PostgreSQL + the server + Caddy (HTTPS), and a runner on your own
machine. The runner polls the server over the internet and executes jobs in
local Docker containers.

> Live deployment: `https://forge-ci.ru`

## Architecture

```
                ┌──────────────────────────────┐         ┌─────────────────────────┐
  https://forge-ci.ru                          │         │  YOUR PC                │
        │                                      │         │                         │
  ┌─────┴─────┐    ┌──────────┐    ┌─────────┐ │  HTTPS  │   ┌───────────────┐      │
  │   Caddy   ├───►│ caddy    │    │ forge-  │◄├─────────┼──►│ forge-runner  │      │
  │  (HTTPS)  │    └──────────┘    │ server  │ │         │   │    (9090)     │      │
  └───────────┘                    └────┬────┘ │         │   └───────┬───────┘      │
                                        │      │         │           │ docker.sock  │
                              ┌─────────▼─────┐│         │   ┌───────▼───────┐      │
                              │ postgres      ││         │   │ job containers│      │
                              │   (5432)      ││         │   │ /workspaces   │      │
                              └───────────────┘│         │   └───────────────┘      │
                              VPS              │         └─────────────────────────┘
                               (server only)   └────────────────────────────────────┘
```

- `forge-server` — HTTP API + scheduler, talks to PostgreSQL. **Runs on the VPS.**
- `forge-runner` — polls the server, checks out the repo, runs each job step
  in a fresh Docker container bound to the shared workspace volume. **Runs on
  your PC** (any machine with Docker can host a runner).
- `postgres` — single-node database.
- `caddy` — reverse proxy providing HTTPS and automatic certs.

## 1. Buy a domain

Any registrar works. If you are in Russia, popular options are
[reg.ru](https://www.reg.ru), [nic.ru](https://www.nic.ru),
[sprinthost.ru](https://sprinthost.ru). A `.ru` zone costs roughly 200-400
rub/year. Use a subdomain such as `ci.example.ru` for Forge so the apex stays
free for other services.

## 2. Order a VPS

A 2 GB RAM / 2 vCPU / 40 GB NVMe box is enough for a demo with light jobs.
Take 4 GB if you plan real Maven/Gradle builds. Providers that accept payment
from Russian banks and cards:

| Provider   | Config             | ~Price/month | Notes                          |
|------------|--------------------|--------------|--------------------------------|
| Timeweb    | 2 vCPU / 2 GB      | 400-500 rub  | MSK/SPB, MIR/СБП               |
| RUVDS      | 2 vCPU / 4 GB      | ~1100 rub    | MSK/SPB, MIR/СБП               |
| FastFox    | 2 vCPU / 2-4 GB    | 400-900 rub  | unlimited traffic, MIR/СБП     |
| Selectel   | 2 vCPU / 4 GB      | ~2800 rub    | Tier III DCs, good SLA         |
| Fornex     | 2 vCPU / 4 GB      | from 600 rub | Europe/US, cards + crypto      |

Choose Ubuntu 22.04/24.04 LTS.

## 3. Point the domain at the VPS

Create an `A` record for `ci.example.ru` → the VPS public IPv4. DNS propagation
usually takes a few minutes to a couple of hours.

## 4. Install Docker and clone the repo

```bash
ssh root@YOUR_VPS_IP

# Docker (official script)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Clone forge
git clone https://github.com/you/forge.git /opt/forge
cd /opt/forge
```

## 5. Configure the server

```bash
cp .env.example .env
vim .env   # set FORGE_JWT_SECRET and POSTGRES_PASSWORD to long random values
```

`.env.example`:

```bash
POSTGRES_DB=forge
POSTGRES_USER=forge
POSTGRES_PASSWORD=change-me
FORGE_JWT_SECRET=change-me-to-a-long-random-secret
FORGE_CORS_ORIGINS=https://forge-ci.ru
```

> `FORGE_JWT_SECRET` signs access tokens (min 32 chars) — if it is left empty the
> server generates a random one per boot, which logs everyone out on restart.

The runner credential is created through the API: `POST /api/runners` (requires
a user JWT) returns a one-time registration token. Put that value in the
runner's `FORGE_SERVER_TOKEN` on the machine that runs jobs. Keep it private.

## 6. Start the server (on the VPS)

```bash
docker compose up -d --build
docker compose ps
```

- Server: `http://YOUR_VPS_IP:8080`, Swagger UI at `/swagger-ui.html`
- The compose file on the VPS runs **postgres + forge-server + caddy** — no
  runner. Runners run on your own machines.

## 7. HTTPS with Caddy

Caddy is included in `docker-compose.yml`. `docker/Caddyfile`:

```
forge-ci.ru {
    reverse_proxy forge-server:8080
}
```

Restart:

```bash
docker compose up -d
```

Forge is now available at `https://forge-ci.ru/swagger-ui.html`.

## 8. Run a runner on your PC

The runner is a worker that polls the server and executes jobs in local Docker
containers. Any machine with Docker can host one — the runner only needs
**outbound** HTTPS access to the server.

On your PC:

```bash
# 1. Clone the repo
git clone git@github.com:vovchekkk/forge-ci.git
cd forge

# 2. Configure the runner
cp .env.runner.example .env.runner
vim .env.runner   # set FORGE_SERVER_TOKEN (from POST /api/runners)

# 3. Start the runner (uses docker-compose.runner.yml)
docker compose -f docker-compose.runner.yml up -d --build
```

`.env.runner.example`:

```bash
FORGE_SERVER_URL=https://forge-ci.ru
FORGE_SERVER_TOKEN=change-me-to-a-long-random-string
FORGE_WORKSPACE_DIR=./workspaces
FORGE_GIT_USERNAME=
FORGE_GIT_PASSWORD=
```

The runner mounts the Docker socket so job containers can be created, and a
local workspace directory for checkouts. It registers itself as `pc-runner`
and appears ONLINE in the server.

### Cloning private repositories

If your projects are private (GitHub/GitLab/etc.), provide credentials to the
runner. For GitHub use a
[personal access token](https://github.com/settings/tokens) — set
`FORGE_GIT_USERNAME` to your username and `FORGE_GIT_PASSWORD` to the token:

```bash
# .env.runner
FORGE_GIT_USERNAME=your-github-username
FORGE_GIT_PASSWORD=ghp_xxxxxxxxxxxxxxxxxxxx
```

The runner uses these with JGit's `UsernamePasswordCredentialsProvider`, which
works for HTTPS basic auth on GitHub, GitLab and Bitbucket (username + token /
password).

## 9. Try it end to end

1. Register a user: `POST /api/auth/register` with an email + password (min 8
   chars). Login (`POST /api/auth/login`) and keep the returned access token.
2. Create a runner credential: `POST /api/runners` with your JWT — returns a
   one-time registration token. Set it as `FORGE_SERVER_TOKEN` for the runner.
3. Create a project pointing at your demo repository (see `examples/`).
4. Start a run from the Swagger UI or the API.
5. Watch the runner pick up the job, clone the repo, and execute each command
   in a container bound to the shared workspace.

## CI/CD: automatic deployment via GitHub Actions

This repo deploys itself: on every push to `main`, GitHub Actions tests the
whole reactor and ships the source to the VPS, then rebuilds the Docker images
in place.

### How it works

- `.github/workflows/ci.yml` — `mvn verify` on push/PR to `main` (all modules,
  including testcontainers integration tests).
- `.github/workflows/deploy.yml` — after CI passes on `main`: `rsync` the
  checkout to `/opt/forge` on the VPS (`.env`, `target/`, local tooling dirs
  excluded) and run `docker compose up -d --build`. Only the server stack is
  deployed; runners live on your own machines and are started separately.

### Required GitHub secrets

| Secret        | Value                                          |
|---------------|------------------------------------------------|
| `VPS_HOST`    | `forge-ci.ru` (or the VPS IP)                  |
| `VPS_USER`    | `root` (or your SSH user)                      |
| `VPS_SSH_KEY` | private SSH key that can log in to the VPS     |

The first deploy needs `/opt/forge/.env` to exist on the server and a valid
Caddyfile (section 7 above). Because `.env` is excluded from the sync it is
never overwritten by a deploy.

## Useful commands

```bash
# On the VPS (server stack)
docker compose logs -f forge-server    # server + scheduler logs
docker compose ps                      # container status
docker compose down                    # stop (keeps the DB volume)
docker compose down -v                 # stop and wipe the DB volume

# On your PC (runner)
docker compose -f docker-compose.runner.yml logs -f forge-runner
docker compose -f docker-compose.runner.yml up -d --build   # (re)start
```

## Troubleshooting

- **Runner stays OFFLINE** — check `FORGE_SERVER_URL` and that `FORGE_SERVER_TOKEN`
  matches a credential created via `POST /api/runners` (not yet revoked); the
  runner needs outbound HTTPS access to `https://forge-ci.ru`.
- **Job fails with "Docker execution failed"** — the runner container needs
  `/var/run/docker.sock` mounted and the job images must be pullable.
- **Workspace bind mount errors** — `FORGE_RUNNER_WORKSPACE` inside the runner
  and the host directory must resolve to the same real path; the default in
  `docker-compose.runner.yml` keeps them aligned.