# Running Suwa Sarana locally

## Backend (Spring Boot 4, Java 21, PostgreSQL 16)

Verified on 2026-10-31 on this machine: jar built, all 37 Flyway migrations (V1..V43) applied to an empty database, demo data
seeded, `GET /actuator/health/readiness` UP, logins, CORS preflight and the refresh-cookie flow all work.

### 1. Database

The dev Postgres container is `suwa-pg-dev` (user `postgres`, password `devonly123`, host port **55432**; ignore port 5433,
a native Postgres owns it).

```powershell
# Docker Desktop must be running (Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'; wait for `docker info`)
docker start suwa-pg-dev
```

The scratch database for this setup is **`suwa_integ`** (already created and left in place, with demo data).
Recreate it from scratch whenever you want a clean slate (the backend seeds the demo data again on first start):

```powershell
docker exec suwa-pg-dev psql -U postgres -c "DROP DATABASE IF EXISTS suwa_integ" -c "CREATE DATABASE suwa_integ"
```

### 2. Build

```bash
cd apps/backend
JAVA_HOME=C:/Users/Kalana/.jdks/corretto-21.0.8 ./mvnw.cmd -q -B -DskipTests package      # -> target/backend-0.0.1-SNAPSHOT.jar
```

### 3. Start (exact command)

Git Bash, from `apps/backend` (the script generates the signing secrets once into the git-ignored `.integ-secrets`):

```bash
./run-integ.sh
```

which is exactly:

```bash
export JAVA_HOME=C:/Users/Kalana/.jdks/corretto-21.0.8
export PORT=8081                                   # 8080 is occupied on this machine by an Apache/WSL listener
export SPRING_PROFILES_ACTIVE=dev                  # dev profile = throw-away defaults + demo data seeding
export DB_URL=jdbc:postgresql://localhost:55432/suwa_integ
export DB_USER=postgres
export DB_PASSWORD=devonly123
export JWT_SECRET=<base64 of 64 random bytes>      # generated into apps/backend/.integ-secrets
export AUTH_HMAC_SECRET=<40 random characters>     # same file
export CORS_ALLOWED_ORIGIN=http://localhost:3000
export FRONTEND_URL=http://localhost:3000
export PUBLIC_BASE_URL=http://localhost:8081
export DEMO_PASSWORD='Demo-Pass-12345'
"$JAVA_HOME/bin/java" -Xmx512m -jar target/backend-0.0.1-SNAPSHOT.jar
```

Startup takes about 11 s (plus ~2 s to seed on the first start). Ready when
`curl http://localhost:8081/actuator/health/readiness` answers `{"status":"UP"}`.
Point the frontend at it with `NEXT_PUBLIC_API_URL=http://localhost:8081/api` (`src/lib/constants.ts` defaults to port 8080).
To use port 8080 instead, free it and run `PORT=8080 ./run-integ.sh`.

Stop: close the terminal / Ctrl+C, or
`Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ? { $_.CommandLine -match 'backend-0.0.1' } | % { Stop-Process -Id $_.ProcessId -Force }`.
The app is **stopped** now; the database `suwa_integ` is left in place.

### 4. Demo accounts

All demo accounts have the password **`Demo-Pass-12345`** (the `DEMO_PASSWORD` above) and a confirmed e-mail. Log in with
`POST /api/auth/login {"username": "<email or phone>", "password": "..."}`.

| Role | E-mail | Notes |
|---|---|---|
| ADMIN | `admin@demo.suwasarana.test` | |
| HOSPITAL_REQUESTER (VERIFIED) | `hospital1@demo.suwasarana.test` ... `hospital8@...` | "General Hospital Colombo (Demo)" etc.; can publish camps/slots, record stock |
| BLOOD_BANK_REQUESTER (VERIFIED) | `bloodbank1@demo.suwasarana.test` ... `bloodbank4@...` | have stock, thresholds, low/critical examples |
| REQUESTER (VERIFIED) | `requester1@demo.suwasarana.test` ... `requester4@...` | have requests in every state |
| DONOR (VERIFIED) | `donor1@demo.suwasarana.test` ... `donor60@...` | profiles, donation history, bookings, matches; donors 1-25 cover each district once |
| awaiting verification | `hospital.pending@`, `requester.pending1@`, `requester.pending2@` (`@demo.suwasarana.test`) | for the admin verification queue |
| rejected | `bloodbank.rejected@demo.suwasarana.test` | cannot publish (403 `verification.rejected`) |

Seeded volume: 81 users, 60 donor profiles, 140 donations, 42 requests (all statuses), 182 matches, 12 camps / 78 slots /
86 bookings, 12 stock sites / 102 batches, 25 districts with data (`GET /api/analytics/summary` -> 18 active requests,
fulfilment rate 50 %, `districtsWithData: 25`).

The dev profile additionally runs the older `DataInitializer`, which adds `admin@test.com`, `hospital@test.com`,
`donor@test.com`, `donor2@test.com`, `donor3@test.com`, `requester@test.com`, `pending_hospital@test.com`,
`unverified@test.com` with password **`password123`** (dev only; never exists with the `prod` profile).

### 5. Things to know

* **Registering a new user locally needs an OTP, and nobody can read it**: the dev SMS provider logs only the masked number and
  the message length (no personal data in logs), and OTPs are stored hashed. Use the demo accounts, or set
  `SMS_PROVIDER=notifylk` with real credentials, or insert a verified phone for the test (`otp_verifications`).
* Cookies: in dev the refresh cookie is `HttpOnly; SameSite=Lax; Path=/api/auth` without `Secure` (`COOKIE_SECURE=false`).
  Frontend on `http://localhost:3000` and API on `http://localhost:8081` are the same site, so Lax works; call `refresh`
  with `credentials: 'include'`.
* Swagger UI is ADMIN-only: log in as admin, then open `/swagger-ui/index.html` with the bearer token.
* `/api/analytics/summary` and `/api/analytics/district-summary` require a signed-in user (any role); only
  `/api/meta/districts`, `/api/inventory/stock-signals`, `/api/camps`, circle pages, `/api/faq/ask` and `/api/contact` are public.
* Tests: `./mvnw.cmd -q -B test` from `apps/backend` (set `REQUIRE_DOCKER=true` so the Testcontainers/Postgres tests fail
  instead of being skipped when Docker is down).
* API reference: `docs/API-CONTRACT.md`.
