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
| Token blacklist | Redis (Railway) |
| Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Gradle |
| Deploy | Docker · Hetzner VPS · GitHub Actions CI/CD |

## Security design decisions

**Secret management:** JWT secret is injected via `@Value("${jwt.secret}")` from environment variables — never hardcoded.

**Token blacklist in Redis, not MySQL:** On every authenticated request the Gateway checks Redis (~1 ms) rather than querying the database. Redis key expiry mirrors token TTL so the blacklist is self-cleaning.

**Isolated auth boundary:** Downstream services (API Service) have no JWT dependency. The Gateway validates once and injects plain HTTP headers (`X-User-Name`, `X-User-Roles`). Swapping the auth mechanism only requires changes here and in the Gateway.

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
