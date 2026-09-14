# Suwa Sarana (සුව සරණ / சுவ சரண) — Emergency Blood Donation & Dispatch Platform

[![CI Pipeline](https://github.com/Dhanushkamg/Suwa-Sarana/actions/workflows/ci.yml/badge.svg)](https://github.com/Dhanushkamg/Suwa-Sarana/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Next.js 14](https://img.shields.io/badge/Next.js-14-black?logo=next.js&logoColor=white)](https://nextjs.org/)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)

**Suwa Sarana** is a real-time, emergency blood coordination and donor dispatch platform built for Sri Lanka. It bridges the critical response gap between patients, hospitals, and voluntary donors using spatial proximity matching, automated notification cascades, private replacement circles, interactive district shortage heatmaps, and Groq-powered multilingual AI assistants.

---

## 🌟 Key Platform Features

- **🌐 Trilingual Localization**: Native support for **English**, **Sinhala (සිංහල)**, and **Tamil (தமிழ்)** across all public and authenticated interfaces.
- **📍 Geo-Spatial Proximity Matching**: Sub-millisecond spherical distance calculations (PostgreSQL `earthdistance`) locating the closest eligible donors within progressive search radii (5km → 15km → 25km).
- **⚡ Two-Tier Notification Cascade**: Real-time Server-Sent Events (SSE) notification stream backed by an automated SMS fallback cascade when donors are offline.
- **🛡️ Requester & Hospital Trust Verification**: Multi-factor identity and hospital verification portal with critical requisition access control.
- **🔐 Google OAuth Authentication**: Seamless, secure sign-in using Google Identity Services (FedCM optimized), integrated alongside robust custom JWT session management.
- **🔒 Security Hardening & Rate Limiting**: Token-bucket rate limiting via Bucket4j, strict CORS protection, secure JWT authentication with refresh token rotation, and robust Content-Security-Policy (CSP) enforcement.
- **👥 Private Replacement-Donor Circles**: Secure, tokenized share links allowing families to rally private volunteer circles before/alongside public dispatch.
- **🖼️ Shareable Open Graph Cards**: Dynamic 1200×630 emergency requisition summary cards generated for seamless Facebook, WhatsApp, and Viber community sharing without exposing private patient identity.
- **🏥 Comprehensive Donation Camp & Blood Bank Management**: Dedicated dashboards for Hospitals and Blood Banks to securely organize, reschedule, and manage national donation camps.
- **🖼️ Automated High-Res Poster Generator**: Instant, visually stunning, and highly professional JPG campaign posters automatically generated for organizers to share on social media to attract donors.
- **📅 Walk-in & Appointment Slot Booking**: Sophisticated appointment booking system with QR code check-ins for smooth donor flow and traffic management at camps.
- **🩸 Intelligent Donor-Camp Filtering**: Donors are algorithmically recommended donation drives that precisely match their verified blood type to minimize wastage and prioritize urgent needs.
- **🗺️ Interactive Admin Geospatial Map**: Leaflet choropleth visualization monitoring live blood inventory, donor density, shortage indicators, and a real-time geographical view of all active donation camps across the 25 districts.
- **🤖 Multilingual AI Request Intake (Groq LLM)**: Natural language conversational request intake extracting blood type, urgency, hospital, and units needed from unstructured text in EN, SI, or TA.
- **💬 Grounded AI Donor FAQ Chatbot**: Trilingual eligibility assistant grounded strictly in National Blood Transfusion Service (NBTS) deferral rules.
- **⚖️ AI Fraud & Duplicate Triage Console**: Advisory AI scoring engine that prioritizes the moderation queue for human administrators with explainable diagnostic flags.
- **📖 OpenAPI 3.0 & Swagger UI**: Full interactive API documentation available at `/swagger-ui.html`.
- **🔄 Automated GitHub Actions CI/CD**: Matrix pipeline executing full backend JUnit test suites and frontend Next.js builds on every commit.

---

## 🏗️ Architecture & Technology Stack

```
suwa-sarana/
├── .github/workflows/          # GitHub Actions CI/CD pipeline
├── apps/
│   ├── backend/                # Spring Boot 3.2 (Java 21), Spring Security, Flyway, JPA
│   └── frontend/               # Next.js 14 App Router, TypeScript, Tailwind CSS, Leaflet
├── packages/
│   └── shared-types/           # Shared TypeScript interfaces
├── docs/                       # Architectural specs, audit notes & deployment runbook
├── docker-compose.yml          # Local multi-service container orchestration
└── .env.example                # Root environment configuration template
```

### Backend
- **Framework**: Spring Boot 3.2 / Java 21
- **Database**: PostgreSQL 16 (`cube` + `earthdistance` extensions)
- **Migrations**: Flyway (7 versioned migrations: `V1` to `V7`)
- **Security & Limiting**: Spring Security 6, JJWT (0.12.5), Bucket4j (8.10.1)
- **API Documentation**: SpringDoc OpenAPI 3.0 / Swagger UI
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Spring Security Test, H2 In-Memory

### Frontend
- **Framework**: Next.js 14 (App Router)
- **Authentication**: Google OAuth2 (Google Identity Services) + Custom JWT
- **Styling**: Tailwind CSS, Radix UI Primitives, Lucide Icons
- **Mapping & Visuals**: Leaflet, React-Leaflet
- **Internationalization**: React Context i18n (`messages/{en,si,ta}.json`)
- **Data Layer**: Centralized `apiClient.ts` with SSE connection management

---

## 🚀 Quickstart Guide

### Prerequisites
- Java 21 JDK
- Node.js 20+ and pnpm 9+
- Docker & Docker Compose (or local PostgreSQL 16)

### 1. Clone & Configure
```bash
git clone https://github.com/Dhanushkamg/Suwa-Sarana.git
cd Suwa-Sarana
cp .env.example .env
```

### 2. Launch with Docker Compose
```bash
docker compose up -d --build
```
- **Web Application**: [http://localhost:3000](http://localhost:3000)
- **REST API Server**: [http://localhost:8080/api](http://localhost:8080/api)
- **Swagger Documentation**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### 3. Running Locally (Development Mode)

#### Start Backend
```bash
cd apps/backend
./mvnw spring-boot:run
```

#### Start Frontend
```bash
# From workspace root:
pnpm install
pnpm --filter frontend dev
```

---

## 🧪 Test Verification

### Backend JUnit Test Suite
```bash
cd apps/backend
./mvnw test
```
*Executes all 63 unit, integration, rate-limiting, and security tests.*

### Frontend Production Build
```bash
cd apps/frontend
pnpm build
```
*Validates static generation, TypeScript types, and server components across all 21+ routes.*

---

## 📚 Documentation Links

- [Production Deployment Guide](file:///d:/My%20new%20projects/Suwa%20sarana/docs/DEPLOYMENT.md) (`docs/DEPLOYMENT.md`)
- [Master Implementation Plan](file:///d:/My%20new%20projects/Suwa%20sarana/docs/MASTER_PLAN.md) (`docs/MASTER_PLAN.md`)
- [Repository Audit Notes](file:///d:/My%20new%20projects/Suwa%20sarana/docs/AUDIT_NOTES.md) (`docs/AUDIT_NOTES.md`)

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
