# Suwa Sarana — Production Deployment & Operations Guide

This guide provides end-to-end instructions for provisioning, configuring, containerizing, and deploying the **Suwa Sarana** blood coordination platform to production environments.

---

## Architecture Overview

```mermaid
graph TD
    Client[Web & Mobile Browsers] -->|HTTPS / Next.js 16| Frontend[Vercel / Next.js Frontend]
    Frontend -->|REST API / SSE| Backend[Spring Boot 3.2 Backend]
    Backend -->|JDBC / JPA / Flyway| DB[(PostgreSQL 16 + cube/earthdistance)]
    Backend -->|HTTPS / REST| Groq[Groq LLM API - Llama 3.3]
    Backend -->|HTTPS / REST| SMS[SMS Gateway Provider]
```

- **Frontend**: Next.js 16 App Router, TypeScript, Tailwind CSS, Leaflet Maps, Lucide Icons. Hosted on Vercel or containerized with Node 22.
- **Backend**: Spring Boot 3.2 / Java 21, Spring Security, JJWT, Bucket4j rate limiting, Flyway migrations, SpringDoc OpenAPI 3.0. Hosted on Render, Railway, Fly.io, or Docker.
- **Database**: PostgreSQL 16+ with `cube` and `earthdistance` extensions for spherical spatial matching across 25 Sri Lankan districts.
- **AI Services**: Groq API (OpenAI-compatible) powering Multilingual Request Drafting (EN/SI/TA), Deferral Rule FAQ Chatbot, and Admin AI Fraud Triage.

---

## 1. Database Provisioning & Setup

Suwa Sarana relies on PostgreSQL native geometric extensions (`cube` and `earthdistance`) for sub-millisecond radius calculations without needing full PostGIS overhead.

### 1.1 Managed PostgreSQL (Supabase, Neon, AWS RDS, Railway)
Connect to your PostgreSQL database with administrative privileges and execute:

```sql
-- Enable spherical distance extensions
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

-- Verify extension installation
SELECT earth_distance(ll_to_earth(6.9271, 79.8612), ll_to_earth(7.2906, 80.6337)) / 1000.0 AS distance_km;
-- Expected output: ~96.2 km (Colombo to Kandy)
```

### 1.2 Automated Flyway Migrations
Flyway runs automatically on Spring Boot application startup (`apps/backend/src/main/resources/db/migration`, versions `V1`–`V21`). Hibernate never touches the schema (`JPA_DDL_AUTO=none` by default; set it to `validate` if you want Hibernate to check entities against the schema at startup).

Two things to know:
- `V3` and `V10` seed demo accounts (`admin@test.com`, ...) with a publicly known password. `V20` disables those logins in every environment; only local development brings them back (`SEED_DATA_ENABLED=true`).
- Public registration cannot create `ADMIN` accounts. Create the first admin by registering normally, then promote the account in the database:

```sql
UPDATE users SET role = 'ADMIN', verification_status = 'VERIFIED' WHERE email = 'you@example.org';
```

---

## 2. Environment Variables Matrix

### Backend (`apps/backend/.env` or Platform Environment Variables)

| Variable | Required | Description | Example / Default |
| :--- | :--- | :--- | :--- |
| `DB_URL` | **Yes** | JDBC connection string to PostgreSQL | `jdbc:postgresql://postgres.db.host:5432/suwa_sarana_prod` |
| `DB_USER` | **Yes** | Database username | `postgres` |
| `DB_PASSWORD` | **Yes** | Database password | `StrongSecretPass123!` |
| `JWT_SECRET` | **Yes** | Base64-encoded 256-bit+ HMAC secret key | `dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LXRvLW1lZXQtam1hYy1yZXF1aXJlbWVudHM=` |
| `CORS_ALLOWED_ORIGIN` | **Yes** | Allowed frontend domain (no wildcard in prod) | `https://suwasarana.lk` |
| `SMS_PROVIDER_API_KEY` | Optional | SMS gateway provider API key | `sms_live_key_xxx` (default: `mock_key`) |
| `SMS_CASCADE_TIMEOUT_SECONDS`| Optional | Timeout before SMS fallback triggers | `180` (3 minutes) |
| `GROQ_API_KEY` | Optional | Groq Cloud API key for AI features | `gsk_xxxxxxxxxxxxxxxxxxxxxxxx` |
| `GOOGLE_CLIENT_ID` | Optional | Google OAuth client ID used to verify Google sign-in tokens | `1234-abc.apps.googleusercontent.com` |
| `COOKIE_SECURE` | Optional | Send the refresh-token cookie over HTTPS only | `true` (default) |
| `COOKIE_SAME_SITE` | Optional | `Lax` when frontend and API share a site; `None` when they are on different sites | `Lax` (default) |
| `SEED_DATA_ENABLED` | **Must be `false`** | Local-only demo accounts and sample data | `false` (default) |
| `SPRING_PROFILES_ACTIVE` | Recommended | Set to `prod`: the app then refuses to start with the development JWT secret or with seed data enabled | `prod` |

### Frontend (`apps/frontend/.env.production` or Vercel Environment Variables)

| Variable | Required | Description | Example |
| :--- | :--- | :--- | :--- |
| `NEXT_PUBLIC_API_URL` | **Yes** | Backend REST API base URL | `https://api.suwasarana.lk/api` |
| `NEXT_PUBLIC_SITE_URL` | **Yes** | Public origin of the website (canonical URLs, sitemap, share cards) | `https://suwasarana.lk` |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | Optional | Google sign-in; the button is hidden when empty | `1234-abc.apps.googleusercontent.com` |

> `NEXT_PUBLIC_*` values are compiled into the browser bundle at **build** time. With Docker, pass them as build args (see `docker-compose.yml`); changing them later requires a rebuild.

---

## 3. Containerized Deployment with Docker Compose

To test or deploy the full stack locally or on a VPS (Ubuntu/Debian):

```bash
# 1. Clone repository
git clone https://github.com/Dhanushkamg/Suwa-Sarana.git
cd Suwa-Sarana

# 2. Configure environment variables in docker-compose.yml or .env
cp .env.example .env

# 3. Build and launch all services
docker compose up -d --build

# 4. Check service health
docker compose ps
```

- **Frontend UI**: `http://localhost:3000`
- **Backend API**: `http://localhost:8080/api`
- **Swagger / OpenAPI Documentation**: `http://localhost:8080/swagger-ui.html`

---

## 4. Deploying Backend to Cloud Platforms

### 4.1 Render / Railway / Fly.io

1. **Create a new Web Service**: Connect your GitHub repository `Suwa-Sarana`.
2. **Root Directory**: `apps/backend`
3. **Environment**: `Java` (or Docker)
4. **Build Command**: `./mvnw clean package -DskipTests`
5. **Start Command**: `java -jar target/backend-0.0.1-SNAPSHOT.jar`
6. **Set Environment Variables**: Set `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGIN`, `GROQ_API_KEY`, and `SPRING_PROFILES_ACTIVE=prod`. If the frontend and API are on different sites (e.g. `vercel.app` and `onrender.com`), also set `COOKIE_SAME_SITE=None`.

---

## 5. Deploying Frontend to Vercel

1. **Import Project**: Import the `Suwa-Sarana` repository into Vercel.
2. **Root Directory**: Select `apps/frontend`.
3. **Framework Preset**: `Next.js` (Node 22, pnpm 12).
4. **Build Command**: `pnpm build` (or default Next.js build).
5. **Output Directory**: `.next`.
6. **Environment Variables**:
   - `NEXT_PUBLIC_API_URL`: URL to your live backend (e.g. `https://api.suwasarana.lk/api`).
   - `NEXT_PUBLIC_SITE_URL`: your public site origin (e.g. `https://suwasarana.lk`).
   - `NEXT_PUBLIC_GOOGLE_CLIENT_ID`: optional, enables Google sign-in.
7. **Deploy**: Click **Deploy**.

---

## 6. Zero-Secret Production Security Checklist

- [x] **No Hardcoded Secrets**: Ensure `application.yml` uses `${ENV_VAR:fallback}` only for local dev defaults.
- [x] **CORS Origin Strictness**: Verify `CORS_ALLOWED_ORIGIN` is configured to the exact production frontend domain (never `*`).
- [x] **Rate Limiting**: Built-in Bucket4j token bucket rate-limits login, register, OTP send/verify, password reset, Google sign-in and `POST /api/requests` (per IP). OTP codes also lock after 5 wrong guesses and can be re-sent only once a minute.
- [x] **No Public Admin Sign-up**: `ADMIN` accounts cannot be created through registration.
- [x] **Fail-fast Production Profile**: with `SPRING_PROFILES_ACTIVE=prod` the app refuses to start on the development JWT secret or with seed data enabled.
- [x] **Role-Based Authorization**: Critical blood requisitions restricted to verified hospital and admin accounts.
- [x] **Private Circles / Share Cards**: Public URLs `/circle/[token]` and `/share/[token]` intentionally omit personal patient identities and NIC numbers.
- [x] **Advisory AI Guardrails**: AI triage scoring never automatically alters request statuses—human administrator approval is always required.
