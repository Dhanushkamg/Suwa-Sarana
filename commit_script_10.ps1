$ErrorActionPreference = "Stop"

Write-Host "Creating Commit 1: Database Migrations and Core Domain Entities"
git add apps/backend/src/main/resources/db/migration/V15__add_camp_requirements_and_roles.sql
git add apps/backend/src/main/java/com/suwasarana/api/camp/DonationCamp.java
git commit -m "feat(db): add V15 migration and update DonationCamp entity with required blood groups"

Write-Host "Creating Commit 2: Data Transfer Objects"
git add apps/backend/src/main/java/com/suwasarana/api/camp/dto/CreateCampDto.java
git add apps/backend/src/main/java/com/suwasarana/api/camp/dto/DonationCampDto.java
git commit -m "feat(api): update donation camp DTOs with blood requirement fields"

Write-Host "Creating Commit 3: Security Configurations and Enums"
git add apps/backend/src/main/java/com/suwasarana/api/user/Role.java
git add apps/backend/src/main/java/com/suwasarana/api/config/SecurityConfig.java
git add apps/backend/src/main/java/com/suwasarana/api/security/JwtAuthFilter.java
git commit -m "feat(security): register BLOOD_BANK_REQUESTER role and secure camp endpoints"

Write-Host "Creating Commit 4: Backend Business Logic and Controllers"
git add apps/backend/src/main/java/com/suwasarana/api/camp/DonationCampService.java
git add apps/backend/src/main/java/com/suwasarana/api/camp/DonationCampController.java
git commit -m "feat(api): implement intelligent donor filtering and organizer-specific camp views"

Write-Host "Creating Commit 5: Application Settings"
git add apps/backend/src/main/resources/application.yml
git commit -m "chore(config): update backend application properties"

Write-Host "Creating Commit 6: Frontend Dependencies and Setup"
git add apps/frontend/package.json
git add apps/frontend/pnpm-lock.yaml
git commit -m "build(deps): integrate html-to-image and leaflet libraries for frontend mapping and poster generation"

Write-Host "Creating Commit 7: Types and Global Interfaces"
git add apps/frontend/src/types/index.ts
git commit -m "type(auth): extend User type to support Blood Bank Requester authentication"

Write-Host "Creating Commit 8: UI Components for Camp Management"
git add apps/frontend/src/components/camps/
git commit -m "feat(ui): build high-fidelity CampPostGenerator and robust CampManagerDashboard"

Write-Host "Creating Commit 9: Dashboard Integration and Auth Registration"
git add apps/frontend/src/app/dashboard/hospital/page.tsx
git add apps/frontend/src/app/dashboard/blood-bank/
git add apps/frontend/src/app/dashboard/admin/camps/
git add apps/frontend/src/app/(auth)/register/page.tsx
git commit -m "feat(dashboard): integrate blood bank roles, admin geospatial map, and hospital camp workflows"

Write-Host "Creating Commit 10: Public Views and Documentation Updates"
git add apps/frontend/src/app/camps/page.tsx
git add README.md
git commit -m "docs(readme): highlight new camp management and slot booking features and enhance donor UI"

Write-Host "Pushing to GitHub..."
git push origin main

Write-Host "All done!"
