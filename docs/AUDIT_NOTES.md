# Repository Audit Notes

## Backend Test Baseline
- Run command: `.\mvnw.cmd clean test`
- Results: Tests run: 21, Failures: 0, Errors: 0, Skipped: 1.
- Existing tests found:
  - `com.suwasarana.api.auth.AuthServiceTest`
  - `com.suwasarana.api.donor.DeferralServiceTest`
  - `com.suwasarana.api.donor.ReliabilityScoreServiceTest`
  - `com.suwasarana.api.matching.EscalationSchedulerTest`
  - `com.suwasarana.api.matching.MatchingEngineTest`
- *Note:* It appears that many of the Milestone 1 tests have already been implemented or partially implemented in the current branch. `RequestControllerIntegrationTest.java` was not seen in the test output.

## Flyway Migrations
- `V1__init_schema.sql` (Schema setup)
- `V2__add_trust_and_safety_tables.sql` (Security & gamification)
- `V3__seed_hospital_and_admin_accounts.sql` (Seed data)
- No `V4` or later migrations exist yet.

## Frontend Mocks
Found 4 pages currently using mocked fallback data:
1. `apps/frontend/src/app/dashboard/requests/page.tsx` (Line 22)
2. `apps/frontend/src/app/dashboard/donor/page.tsx` (Line 67)
3. `apps/frontend/src/app/dashboard/donor/matches/page.tsx` (Line 32)
4. `apps/frontend/src/app/dashboard/admin/page.tsx` (Line 37)

## Actionable Takeaways
1. Milestone 1 needs to be verified to ensure `RequestControllerIntegrationTest` exists and coverage meets the 70% threshold.
2. The frontend requires complete API wiring in Milestone 3 for the above 4 mock locations.
