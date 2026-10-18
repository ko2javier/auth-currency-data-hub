# Auth Service — KO2 Platform

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-HS256-black?logo=jsonwebtokens)
![Docker](https://img.shields.io/badge/Docker-deployed-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Authentication microservice responsible for user login, token issuance, logout with token blacklisting, and role-based access control. Part of the [KO2 Platform](https://github.com/ko2javier/server-infrastructure) microservices ecosystem.

**Live demo:** [hub.ko2-oreilly.com](https://hub.ko2-oreilly.com)

---

## What it does

- Issues signed JWT tokens on login (HS256, configurable expiry)
- Stores token blacklist in Redis on logout — tokens self-expire without cron jobs
- Validates credentials against MySQL with BCrypt password hashing
- Exposes roles (`ROLE_USER`, `ROLE_ADMIN`) for downstream authorization

## Auth flow

```
POST /auth/login
  → validate credentials (BCrypt)
  → issue JWT (HS256, signed with secret from env)
  → return token

POST /auth/logout
  → add token to Redis blacklist (TTL = remaining token lifetime)
  → token is self-cleaning — no manual cleanup needed

POST /auth/validate  ← called by the Gateway on every request
  → check signature
  → check Redis blacklist
  → return user details if valid
```

## Endpoints

| Method | Path | Auth required | Description |
|---|---|---|---|
| `POST` | `/auth/login` | No | Returns JWT |
| `POST` | `/auth/logout` | Bearer token | Blacklists token |
| `POST` | `/auth/validate` | Bearer token | Validates token (internal use) |

## Tech stack

| | |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security + JJWT (HS256) + BCrypt |
| Database | Spring Data JPA + MySQL (Aiven) |
| Token blacklist | Redis 7.2 (self-hosted, same Docker network) |
| Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Tests | JUnit 5 + Testcontainers (real MySQL and Redis) — 9 integration tests |
| Build | Gradle |
| Deploy | GitHub Actions → ghcr.io → Hetzner VPS, pinned by commit SHA |

## Security design decisions

**Secret management:** JWT secret is injected via `@Value("${jwt.secret}")` from environment variables — never hardcoded.

**Token blacklist in Redis, not MySQL:** On every authenticated request the Gateway checks Redis (~1 ms) rather than querying the database. Redis key expiry mirrors token TTL so the blacklist is self-cleaning.

**Logout and the gateway share a Redis key, not an API.** `logout` writes the token (without the `Bearer ` prefix) with a TTL equal to its remaining lifetime; the gateway only asks `hasKey(token)`. If either side changed the key shape the revoked token would keep working, with no error in either service — so a test pins the exact key, value and TTL.

**Isolated auth boundary:** Downstream services (API Service) have no JWT dependency. The Gateway validates once and injects plain HTTP headers (`X-User-Name`, `X-User-Roles`). Swapping the auth mechanism only requires changes here and in the Gateway.

## Tests

```bash
./gradlew test            # 9 tests, ~1 min
```

They need Docker: a **real MySQL and a real Redis** run in containers (Testcontainers). The login reads the user and its roles through a JPA many-to-many that a mock would never execute, and the logout writes the blacklist the gateway reads — the contract between the two services — so neither is mocked. Tables are created by Hibernate (`ddl-auto=update`), so there is no schema file to keep in sync. Kafka is not started: this service only produces events, and the producer is replaced by a test double.

What they pin: the claims inside the issued token (subject, roles, one-hour expiry), the client IP taken from `X-Forwarded-For` for the login event, the same response for a wrong password and an unknown user, and the exact blacklist entry written by logout.

**The suite was tested against itself.** Four faults were injected one at a time — storing the token with its `Bearer ` prefix, dropping the roles claim, ignoring `X-Forwarded-For`, and skipping the password check — and each turns the suite red.

CI runs them on every push to `master` **before** anything is built or deployed.

## Deployment

```
push to master ──▶ tests          9 tests, real MySQL + Redis via Testcontainers
                     └──▶ build   image to ghcr.io, tagged `latest` and the commit SHA
                            └──▶ deploy   SSH: pull that SHA, restart, wait for 200
```

Each stage gates the next. **The image is built here, not on the VPS**, which used to spend minutes of compilation and scarce RAM on every push. Deploys pin the commit SHA rather than `latest`, so the running container states which code it serves and a rollback is the same command with the previous SHA.

## Part of the KO2 Platform

```
Frontend (Angular 19 · Vercel)
    └── API Gateway :7000  ← routes & validates JWT
            ├── Auth Service :4000  ← this repo
            └── API Service :5000  ← weather + currency + cache
```

→ [server-infrastructure](https://github.com/ko2javier/server-infrastructure) — full architecture, Docker Compose, live demo credentials

## License

MIT
