# Forge CI

**Forge CI** — свой GitHub Actions, у которого оркестрация живёт на общем сервере, а пайплайны выполняются в Docker-контейнерах на **вашей** машине, которую вы подключаете как раннер.

Пайплайны описываются коротким YAML-манифестом, планируются на сервере, а исполняются у вас — раннеры забирают задания, клонируют репозиторий и прогоняют каждый шаг в свежем контейнере.

> Сервер: **https://forge-ci.ru** (Swagger UI: `/swagger-ui.html`)

---

## Что это и зачем

CI/CD — это автоматизация: каждое изменение в репозитории должно пройти проверки (тесты, линтеры, сборка) прежде чем попасть в прод. Готовые сервисы (GitHub Actions, GitLab CI) требуют, чтобы ваш код лежал у них, привязывают к своей экосистеме и берут плату за раннер-минуты.

**Forge CI** решает это иначе:

-  **Код работает у вас** — сервер только координирует, репозиторий клонируется и собирается на ваших машинах.
-  **Раннеры на чём угодно** — ПК, ноутбук, выделенный сервер, Raspberry Pi. Любая машина с Docker.
-  **Бесплатные вычисления** — вы используете своё железо, а не арендуете раннеры.
-  **Мультитенантность из коробки** — каждый пользователь видит только свои проекты, пайплайны, прогоны и раннеры.

## Возможности

### Оркестрация пайплайнов
- **YAML-манифесты** — пайплайн описывается в 7 слов: `name`, `image`, `jobs`, `commands`, `needs`, `timeout`, `environment`.
- **DAG-зависимости** — через `needs` строится граф выполнения, джобы с готовыми зависимостями запускаются параллельно/по порядку.
- **Валидация при создании** — пустые команды, неизвестные зависимости, самозависимости и циклические ссылки отсекаются до запуска.
- **Правильная стейт-машина** — у прогона и джобы формализованные переходы состояний, незаконные переходы физически невозможны (`PENDING → QUEUED → RUNNING → SUCCESS/FAILED/CANCELED`).
- **Отмена и таймауты** — прогон можно отменить на лету (контейнер убивается, зависимые джобы получают `SKIPPED`), у каждой джобы свой `timeout`.
- **Skip-логика** — при падении джобы зависимые автоматически пропускаются, прогон завершается `FAILED`.

### Безопасность (production-grade)
- **JWT-аутентификация** — HMAC-SHA256 access-токены (15 мин) + refresh-токены (30 дней).
- **Ротация refresh-токенов** — каждый refresh выдаёт новую пару, переиспользование старого токена обнаруживается и аннулирует всю семью.
- **Хэширование токенов** — и refresh-токены, и креды раннеров хранятся только как SHA-256 (даже в БД нельзя украсть рабочий токен), сравнение constant-time.
- **BCrypt (cost 12)** для паролей.
- **Защита от брутфорса** — 5 неудачных попыток → блокировка входа на 10 минут.
- **Независимая аутентификация раннеров** — отдельный заголовок `X-Forge-Runner-Token`, раннеры не смешиваются с пользователями.
- **Append-only аудит-лог** — события входа, выхода, регистрации, throttle записываются в отдельную таблицу (без паролей и токенов).
- **Изоляция прав (owner-scoped)** — каждый запрос проверяет `ownerId`; чужие сущности возвращают 404, не светясь даже фактом существования.
- **Job-контейнеры изолированы** — non-privileged, **без** монтирования docker.sock внутрь джобы, всегда удаляются после завершения (в т.ч. по таймауту и ошибке).

### Раннеры
- **Реестр раннеров** — выпуск одноразовых креденшалов (`POST /api/runners`), перевыпуск и отзыв.
- **Heartbeat + авто-детект падений** — раннер, переставший отвечать за 30 сек, помечается OFFLINE, а его in-flight джобы автоматически падают с «Runner went offline».
- **Атомарный claim** — `FOR UPDATE SKIP LOCKED` в PostgreSQL: несколько раннеров физически не могут получить одну джобу дважды.
- **Изоляция воркспейсов** — у каждой джобы свой каталог, shared named volume `forge-workspace` монтируется и в раннер, и в job-контейнеры (никаких проблем с путями хост/контейнер).
- **Стриминг логов** — вывод джобы построчно пишется в БД и доступен через API.

### Надёжность в нестабильных сетях
- **Retry-инфраструктура** — `RetrySupport` с экспоненциальным backoff + джиттер применяется к отчётам результата (5 попыток), регистрации (5), клонированию (3) и пуллу образов (3).
- **Авто-ретраи Maven** — в каждый job-контейнер инжектится `MAVEN_OPTS` с настройкой ретраев зависимостей; проектам не нужно таскать свой `.mvn/maven.config`.
- **Асинхронное исполнение** — джоба выполняется на фоновом потоке, heartbeat не блокируется (раннер не «умирает» на долгих сборках).

### CI/CD для себя
- **Проект сам проходит CI** — `mvn verify` по всему реактору при каждом push/PR в `main`.
- **HTTP API + Swagger UI** — интерактивная документация всех эндпоинтов.

## Архитектура

```
   ┌───────────────────────────────┐      ┌────────────────────────────────┐
   │        Forge-server           │      │         Forge-runner           │
   │        (forge-ci.ru)          │      │         (ваша машина)          │
   │                               │      │                                │
   │  браузер/API ──► caddy ─┐     │      │  ┌──────────────────────────┐  │
   │                 (HTTPS) │     │      │  │    forge-runner :9090    │  │
   │                         ▼     │      │  │    пуллит · клонирует    │  │
   │               forge-server    │      │  │      создаёт джобы       │  │
   │                  │    :8080   │      │  └───────┬────────────┬─────┘  │
   │                  ▼            │      │          │          mount      │
   │              postgres         │      │ /var/run/docker.sock  │        │
   │               :5432           │      │          │            ▼        │
   └───────────────────────────────┘      │          ▼      ┌────────────┐ │
                   │                      │  ┌────────────┐ │ forge-     │ │
                   │ ▼ ответы:            │  │   Docker   │ │ workspace  │ │
                   │   JobClaim · status  │  │   Engine   │ │ (volume)   │ │
                   │                      │  └──────┬─────┘ └─────┬──────┘ │
                   │ ▲ запросы:           │         │             │        │
                   │   poll · heartbeat   │         ▼             ▼        │
                   │   logs · result      │  ┌──────────────────────────┐  │
                   └──────────────────────│  │ · job-контейнеры         │  │
                                          │  │       non-privileged     │  │
                                          │  │ · без docker.sock внутри │  │
                                          │  │ · эфемерные              │  │
                                          │  │ · /workspace/<jobId>     │  │
                                          │  └──────────────────────────┘  │
                                          └────────────────────────────────┘
```

- **forge-server** — HTTP API, планировщик, реестр раннеров, стейт-машина, аудит. Живёт в хостинге, наружу торчит только Caddy (HTTPS). Связь с раннером **двунаправленная**, но соединение всегда инициирует раннер (исходящие запросы), сервер лишь отвечает.
- **forge-runner** — ваш агент: пуллит сервер, клонирует репозиторий, через docker.sock создаёт job-контейнеры. Работает за NAT, без проброса портов.
- **forge-workspace** — общий named volume: раннер кладёт чекаут в `/workspaces/<jobId>`, job-контейнер монтирует тот же volume в `/workspace/<jobId>`. Никаких проблем с путями хост/контейнер.
- **job-контейнеры** — эфемерные, non-privileged, **без** docker.sock внутри, всегда удаляются после выполнения (в т.ч. по ошибке и таймауту).

### Компоненты

| Компонент | Роль | Где живёт |
|-----------|------|-----------|
| **forge-server** | HTTP API, планировщик, реестр раннеров, стейт-машина, аудит | Хостинг Forge |
| **forge-runner** | Поллит сервер, клонирует репо, исполняет джобы в Docker | Ваша машина с Docker |
| **postgres** | Единственное хранилище (схема управляется Flyway) | Хостинг Forge |
| **caddy** | Reverse proxy, бесплатные HTTPS-сертификаты | Хостинг Forge |

### Ключевые решения

1. **Разделение control plane / data plane.** Сервер только планирует (отвечает на вопрос «что запускать»), а код и вычисления живут на ваших машинах. Раннеру нужен лишь **исходящий** HTTPS к серверу — он работает за NAT, без проброса портов.
2. **Пуллинг вместо websocket.** Раннеры опрашивают сервер (`GET /runners/{id}/jobs/next`) — проще, надёжнее, отлично переживает обрывы связи.
3. **Формализованные стейт-машины.** Переходы состояний джоб и прогонов описаны явно и проверяются в рантайме — гонки и «волшебные» переходы ловятся мгновенно, а не превращаются в молчаливый рассинхрон.
4. **`FOR UPDATE SKIP LOCKED`** для выдачи джоб — распределённая очередь без двойного назначения, честная для любого числа раннеров.
5. **Shared named volume** вместо bind mount — job-контейнеры создаются через host-docker.sock, и пути `/workspaces` внутри контейнера раннера не совпадают с host-путями; named volume решает это навсегда.
6. **Асинхронная джоба на раннере.** Scheduler-поток занимается только heartbeat и claim — долгая Maven-сборка не блокирует регистрацию.
7. **Два класса принсипалов.** Пользователь (JWT) и раннер (runner-token) аутентифицируются разными фильтрами с разными правами доступа.
8. **Flyway + `ddl-auto: validate`.** Миграции — единственный источник схемы, Hibernate лишь проверяет соответствие.

## Технологический стек

**Язык и сборка:** Java 25 (JDK 25, Eclipse Temurin), Maven 3.9+ (мультимодульный реактор: parent POM + 4 модуля), `maven-compiler-plugin`, `spring-boot-maven-plugin` (repackage с classifier `exec`), Maven Surefire 3.5.2.

**Фреймворки:** Spring Boot 3.5.0, Spring Web (MVC + REST), Spring Security (filter-chain, stateless, `BCryptPasswordEncoder` с cost 12), Spring Data JPA (Hibernate, репозитории с `@Lock`/PESSIMISTIC_WRITE и native-запросами с `FOR UPDATE SKIP LOCKED`), Spring Validation (`jakarta.validation`), Spring Actuator (health/info), `spring-security-oauth2-jose` (работа с JWT/JOSE).

**База данных:** PostgreSQL 16 (`postgres:16-alpine`), драйвер `postgresql`, Flyway 10.22.0 (`flyway-core` + `flyway-database-postgresql`) с `ddl-auto: validate`.

**Docker:** docker-java 3.4.2 (`docker-java-core`, `docker-java-transport-zerodep`, `docker-java-transport-httpclient5`) — управление образами/контейнерами через docker.sock без docker CLI; Docker BuildKit (мультистейдж Dockerfile: Maven-образ для сборки → `eclipse-temurin:25-jre` для рантайма); Docker Compose; named volumes (`forge-workspace`, `forge-postgres-data`, `caddy-data`).

**Git:** JGit 7.1.0.202411261347-r — клонирование, чекаут веток/тегов/SHA, `UsernamePasswordCredentialsProvider` для приватных репозиториев, без системного git.

**Сериализация и парсинг:** Jackson 2.18.2 (`jackson-databind`, `jackson-dataformat-yaml`) — парсинг YAML-манифестов пайплайнов и JSON по HTTP-протоколу.

**API и документация:** springdoc 2.8.14 (`springdoc-openapi-starter-webmvc-ui`) — OpenAPI 3, Swagger UI `/swagger-ui.html`.

**Тестирование:** JUnit 5 (`junit-jupiter`), Spring Boot Test (`spring-boot-starter-test`), Spring Security Test, Testcontainers 1.21.3 (`testcontainers`, `testcontainers:postgresql`) — интеграционные и e2e-тесты на реальном Postgres и Docker.

**Инфраструктура:** Docker, Docker Compose, Caddy 2 (reverse proxy + авто-сертификаты Let's Encrypt), GitHub Actions (CI проекта: `mvn -B verify`).

## Структура репозитория

```
├── shared/             # модель пайплайнов, YAML-парсер, валидатор, DTO протокола
├── forge-server/       # Spring Boot сервер: проекты, пайплайны, прогоны, раннеры, планировщик
├── forge-runner/       # Spring Boot агент: поллинг, клонирование, исполнение в Docker
├── forge-e2e/          # сквозные тесты: полный пайплайн на реальных Postgres + Docker
├── docker/             # Dockerfile'ы, Caddyfile
├── docs/               # дополнительные гайды
└── .github/workflows/  # CI проекта
```

## Что реализовано (статус)

- [x] Регистрация/логин/refresh/logout, JWT, ротация refresh-токенов с детекцией переиспользования
- [x] Брутфорс-защита, BCrypt(12), аудит-лог, хэширование токенов
- [x] Проекты (репозитории) + пайплайны (YAML) с валидацией и детекцией циклов
- [x] Прогоны: запуск, отмена, таймауты, skip-логика, DAG-зависимости
- [x] Реестр раннеров, одноразовые креды, heartbeat, авто-детект оффлайна, отзыв
- [x] Атомарный claim (`SKIP LOCKED`), стриминг логов, отчёты результатов
- [x] Исполнение джоб в ephemeral-контейнерах (без привилегий, без docker.sock внутри)
- [x] Retry-инфраструктура (backoff + jitter), авто-ретраи Maven через MAVEN_OPTS
- [x] Асинхронное исполнение джоб (heartbeat не блокируется)
- [x] Тесты: unit + интеграционные (Testcontainers) + e2e
- [x] Проект развёрнут на https://forge-ci.ru

## Быстрый старт

Сервер уже работает на **https://forge-ci.ru**. Всё, что нужно — создать аккаунт, описать пайплайн и подключить свой раннер. Дальше пошаговая инструкция в разделе «Как пользоваться».

Требования для раннера: **Docker** (на любой ОС — Windows, macOS, Linux).

## Как пользоваться (полная инструкция)

Весь обмен — обычные HTTP-запросы, документация доступна в Swagger UI. Ниже полный цикл «от регистрации до первого зелёного прогона».

### Шаг 1. Регистрация

```bash
curl -X POST https://forge-ci.ru/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"supersecret"}'
```

Ответ содержит `accessToken` (живёт 15 минут) и `refreshToken` (30 дней):

```json
{
  "id": "…",
  "email": "you@example.com",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "refreshToken": "a1b2c3…"
}
```

### Шаг 2. Логин (когда токен истёк)

```bash
curl -X POST https://forge-ci.ru/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"supersecret"}'
```

Access-токен короткоживущий — при 401 обновите пару через `/api/auth/refresh` с `refreshToken` (токены ротируются, старая пара аннулируется).

### Шаг 3. Создайте проект (репозиторий)

```bash
curl -X POST https://forge-ci.ru/api/projects \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-app",
    "repositoryUrl": "https://github.com/you/my-app.git",
    "repositoryBranch": "main"
  }'
```

> Для приватных репозиториев укажите креды раннеру (`.env.runner`: `FORGE_GIT_USERNAME` + `FORGE_GIT_PASSWORD`).

### Шаг 4. Опишите пайплайн (YAML)

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

```bash
curl -X POST https://forge-ci.ru/api/projects/$PROJECT_ID/pipelines \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"config": "<yaml из блока выше>"}'
```

При создании пайплайн **сразу валидируется**: пустые команды, неизвестные `needs`, самозависимости и циклы вернутся ошибкой с описанием.

### Шаг 5. Выпустите креденшал раннера

```bash
curl -X POST https://forge-ci.ru/api/runners \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"pc-runner"}'
```

Креденшал показывается **один раз** — сохраните его:

```json
{
  "id": "…",
  "name": "pc-runner",
  "status": "OFFLINE",
  "registrationToken": "x7fG…"
}
```

### Шаг 6. Клонируйте репозиторий Forge CI и запустите раннер

Раннер запускается из этого репозитория (там лежат `docker-compose.runner.yml` и `.env.runner.example`). Склонируйте его на машину с Docker:

```bash
git clone https://github.com/vovchekkk/forge-ci.git
cd forge-ci
```

Скопируйте и настройте конфиг раннера:

```bash
cp .env.runner.example .env.runner
```

`.env.runner` должен выглядеть так:

```bash
# URL сервера (HTTPS) — можно подключаться с любой машины с Docker
FORGE_SERVER_URL=https://forge-ci.ru

# Токен из шага 5 (показывается один раз)
FORGE_SERVER_TOKEN=x7fG…

# Имя, под которым раннер появится в списке раннеров
FORGE_RUNNER_NAME=pc-runner

# Для приватных репозиториев: GitHub username + personal access token
FORGE_GIT_USERNAME=your-github-username
FORGE_GIT_PASSWORD=ghp_xxxxxxxxxxxxxxxxxxxx
```

Запустите раннер:

```bash
docker compose --env-file .env.runner -f docker-compose.runner.yml up -d --build
```

Проверьте, что всё поднялось:

```bash
docker compose -f docker-compose.runner.yml ps
docker compose -f docker-compose.runner.yml logs -f forge-runner   # Ctrl+C для выхода
```

Раннер зарегистрируется, станет `ONLINE` и начнёт опрашивать сервер каждые ~2 секунды.

### Шаг 7. Запустите прогон

```bash
curl -X POST https://forge-ci.ru/api/pipelines/$PIPELINE_ID/runs \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"revision":"main"}'
```

### Шаг 8. Следите за результатом

```bash
# Прогон
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  https://forge-ci.ru/api/pipeline-runs/$RUN_ID

# Джобы прогона (статусы, exit-коды, ошибки)
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  https://forge-ci.ru/api/pipeline-runs/$RUN_ID/jobs

# Логи джобы
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  https://forge-ci.ru/api/jobs/$JOB_ID/logs
```

Статусы прогона: `CREATED → QUEUED → RUNNING → SUCCESS / FAILED / CANCELED`. Отменить прогон: `POST /api/pipeline-runs/{id}/cancel`.

## Справочник по DSL

```yaml
name: <имя пайплайна>                # необязательно
image: <docker-образ по умолчанию>   # необязательно, по умолчанию eclipse-temurin:25-jdk
jobs:
  <job-name>:
    commands:                        # список команд (каждая — в своём контейнере)
      - echo "step 1"
    needs: [<другая-джоба>]          # зависимость; пусто = запуск сразу
    timeout: 600                     # секунды; default 3600
    environment:                     # env-переменные джобы
      KEY: value
```

Правила:
- `commands` **обязательны** и не могут быть пустыми.
- Джоба без `needs` запускается сразу (root); остальные — после успеха всех зависимостей.
- При падении/отмене зависимости джоба получает `SKIPPED`, прогон — `FAILED`/`CANCELED`.
- `needs` должен ссылаться только на существующие джобы и не создавать циклов.

### Примеры

**Простейший:**

```yaml
name: hello
image: alpine
jobs:
  say:
    commands:
      - echo "Hello from Forge CI!"
```

**Java-проект (build → test → package):**

```yaml
name: java-test
image: maven:3.9-eclipse-temurin-25
jobs:
  test:
    commands:
      - mvn test
  package:
    needs: [test]
    commands:
      - mvn package
```

> Maven-ретраи уже встроены: раннер инжектит `MAVEN_OPTS` в каждый контейнер, так что скачивание зависимостей переживает нестабильный интернет.

## Тестирование

```bash
mvn -B verify   # весь реактор: unit + интеграционные + e2e
```

- `shared` — парсер и валидатор DSL (граничные случаи, циклы).
- `forge-server` — JWT, хэширование токенов, ротация refresh, брутфорс-защита, стейт-машины, DAG, планировщик; интеграционные тесты на реальном Postgres (Testcontainers): персистентность, безопасность, конкурентный claim.
- `forge-runner` — Docker-исполнение, Git-чекаут, ретраи, воркспейс, JobRunner, конфиг.
- `forge-e2e` — полный цикл: реальные Postgres + Docker + раннер.

## Решение проблем

| Проблема | Причина / решение |
|----------|-------------------|
| Раннер `OFFLINE` | Проверьте `FORGE_SERVER_URL` и `FORGE_SERVER_TOKEN` (должен совпадать с выданным `POST /api/runners`, не отозван). Нужен исходящий HTTPS до сервера. |
| `Docker execution failed` | Раннер-контейнер должен иметь `/var/run/docker.sock`; образ джобы должен пуллиться. |
| Джоба упала с `Runner went offline` | Раннер не прислал heartbeat 30 сек (интернет/ПК выключен). In-flight джобы авто-падают, чтобы не зависали. |
| Maven TLS-ошибки | Ретраи встроены; если очень нестабильная сеть — можно усилить `MAVEN_OPTS` в `environment` джобы. |
| Диск Docker разросся | Автоочистка раз в сутки через Планировщик Windows: `docker system prune -f` (named volume и работающие контейнеры не трогаются). |

## Лицензия

Pet-проект автора. Исходный код открыт.
