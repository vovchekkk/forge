# Deploying Forge

This guide walks through deploying a self-hosted Forge CI instance for a demo
or light production use: one VPS running PostgreSQL, the server, and one
runner in Docker Compose, fronted by Caddy for HTTPS.

## Architecture

```
                         ┌──────────────────────────────────────────┐
  https://ci.example.ru  │  VPS                                      │
        │                │                                          │
  ┌─────┴─────┐          │   ┌──────────┐    ┌─────────────────┐    │
  │   Caddy   ├─────────►│   │  caddy   │    │                 │    │
  │  (HTTPS)  │          │   └──────────┘    │   forge-server  │    │
  └───────────┘          │                   │    (8080)       │    │
                         │                   └───────┬─────────┘    │
                         │                           │              │
                         │   ┌──────────┐    ┌───────▼─────────┐    │
                         │   │ postgres │    │   forge-runner  │    │
                         │   │  (5432)  │    │    (9090)       │    │
                         │   └──────────┘    └───────┬─────────┘    │
                         │                          │ docker.sock   │
                         │                 ┌────────▼────────┐      │
                         │                 │ job containers  │      │
                         │                 │  (/workspaces)  │      │
                         │                 └─────────────────┘      │
                         └──────────────────────────────────────────┘
```

- `forge-server` — HTTP API + scheduler, talks to PostgreSQL
- `forge-runner` — polls the server, checks out the repo, runs each job step
  in a fresh Docker container bound to the shared workspace volume
- `postgres` — single-node database
- `caddy` — reverse proxy providing HTTPS and automatic certs

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

## 5. Configure

```bash
mkdir -p /srv/forge/workspaces
cp .env.example .env
vim .env   # set FORGE_SERVER_TOKEN to a long random value
```

`.env.example`:

```bash
POSTGRES_DB=forge
POSTGRES_USER=forge
POSTGRES_PASSWORD=change-me
FORGE_SERVER_TOKEN=change-me-to-a-long-random-string
FORGE_WORKSPACE_DIR=/srv/forge/workspaces
```

> The runner token is a shared secret: the runner presents it when registering
> and on every call (`X-Forge-Token`). Keep it private.

### Cloning private repositories

If your projects are private (GitHub/GitLab/etc.), provide credentials to the
runner. For GitHub use a
[personal access token](https://github.com/settings/tokens) — set
`FORGE_GIT_USERNAME` to your username and `FORGE_GIT_PASSWORD` to the token:

```bash
# .env
FORGE_GIT_USERNAME=your-github-username
FORGE_GIT_PASSWORD=ghp_xxxxxxxxxxxxxxxxxxxx
```

The runner uses these with JGit's `UsernamePasswordCredentialsProvider`, which
works for HTTPS basic auth on GitHub, GitLab and Bitbucket (username + token /
password).

## 6. Start

```bash
docker compose up -d --build
docker compose ps
```

- Server: `http://YOUR_VPS_IP:8080`, Swagger UI at `/swagger-ui.html`
- A runner named `docker-runner` registers itself and appears ONLINE.

## 7. HTTPS with Caddy

Add a Caddy service to `docker-compose.yml`:

```yaml
  caddy:
    image: caddy:2
    restart: unless-stopped
    depends_on:
      forge-server:
        condition: service_started
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./docker/Caddyfile:/etc/caddy/Caddyfile
      - caddy-data:/data
    networks:
      - forge-net
```

`docker/Caddyfile`:

```
ci.example.ru {
    reverse_proxy forge-server:8080
}
```

Restart:

```bash
docker compose up -d
```

Forge is now available at `https://ci.example.ru/swagger-ui.html`.

## 8. Try it end to end

1. Create a project pointing at your demo repository (see `examples/`).
2. Start a run from the Swagger UI or the API.
3. Watch the runner pick up the job, clone the repo, and execute each command
   in a container bound to the shared workspace.

## Useful commands

```bash
docker compose logs -f forge-server    # server + scheduler logs
docker compose logs -f forge-runner    # runner activity
docker compose ps                      # container status
docker compose down                    # stop (keeps the DB volume)
docker compose down -v                 # stop and wipe the DB volume
```

## Troubleshooting

- **Runner stays OFFLINE** — check `FORGE_SERVER_URL` and token match the
  server; the runner needs outbound access to `forge-server:8080`.
- **Job fails with "Docker execution failed"** — the runner container needs
  `/var/run/docker.sock` mounted and the job images must be pullable.
- **Workspace bind mount errors** — `FORGE_RUNNER_WORKSPACE` inside the runner
  and the host directory must resolve to the same real path; the default in
  `docker-compose.yml` keeps them aligned.