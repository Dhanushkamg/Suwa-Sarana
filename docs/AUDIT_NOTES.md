# Repository Audit Notes

## Backend Test Baseline
- Run command: `.\mvnw.cmd clean test`
- Results: Tests run: 63, Failures: 0, Errors: 0, Skipped: 1.
- Coverage includes:
  - Security & Rate Limiting
  - Matching Engine & Escalation
  - AI Services (Intake, Triage, FAQ)
  - SMS Fallback & Notifications
  - Core Donor & Authentication Services

## Flyway Migrations
- `V1__init_schema.sql` (Schema setup)
- `V2__add_trust_and_safety_tables.sql` (Security & gamification)
- `V3__seed_hospital_and_admin_accounts.sql` (Seed data)
- `V4__add_verification_status.sql` (Verification flow)
- `V5__add_notification_logs.sql` (Audit logging for SSE/SMS)
- `V6__add_donor_circles.sql` (Private sharing)
- `V7__add_ai_triage_fields.sql` (Fraud AI triage)

## Frontend Mocks & API Integration
- **Status: Complete.** All frontend mock files have been replaced with live API endpoints. Next.js 16 build is stable.

## Deferred Features Implementation Status
- AI Features: **Complete**
- Notification Cascade: **Complete**
- Private Circles & Sharing: **Complete**
- Donation Camp Scheduling: **Pending**
- IVR / Voice Hotline: **Pending**
- Public Stock Signals: **Pending**
- Location Jittering: **Pending**
