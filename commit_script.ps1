git add apps/backend/src/main/resources/db/migration/V14__add_donation_slots.sql
git commit -m "db: add flyway migration for donation slots and bookings"

git add apps/backend/src/main/java/com/suwasarana/api/slot/SlotStatus.java apps/backend/src/main/java/com/suwasarana/api/slot/BookingStatus.java apps/backend/src/main/java/com/suwasarana/api/slot/DonationSlot.java apps/backend/src/main/java/com/suwasarana/api/slot/SlotBooking.java apps/backend/src/main/java/com/suwasarana/api/slot/DonationSlotRepository.java apps/backend/src/main/java/com/suwasarana/api/slot/SlotBookingRepository.java
git commit -m "feat(backend): add core donation slot jpa entities and enums"

git add apps/backend/src/main/java/com/suwasarana/api/slot/CreateSlotDto.java apps/backend/src/main/java/com/suwasarana/api/slot/BulkCreateSlotDto.java apps/backend/src/main/java/com/suwasarana/api/slot/DonationSlotDto.java apps/backend/src/main/java/com/suwasarana/api/slot/SlotBookingDto.java apps/backend/src/main/java/com/suwasarana/api/exception/DonorIneligibleException.java apps/backend/src/main/java/com/suwasarana/api/exception/DuplicateBookingException.java apps/backend/src/main/java/com/suwasarana/api/exception/SlotFullException.java apps/backend/src/main/java/com/suwasarana/api/exception/SlotNotFoundException.java apps/backend/src/main/java/com/suwasarana/api/common/GlobalExceptionHandler.java
git commit -m "feat(backend): add slot booking dtos and custom exceptions"

git add apps/backend/src/main/java/com/suwasarana/api/slot/SlotBookingService.java apps/backend/src/main/java/com/suwasarana/api/notification/NotificationService.java apps/backend/src/main/java/com/suwasarana/api/notification/NotificationCascadeService.java
git commit -m "feat(backend): implement concurrency-safe slot booking service"

git add apps/backend/src/main/java/com/suwasarana/api/slot/SlotBookingController.java apps/backend/src/main/java/com/suwasarana/api/config/SecurityConfig.java apps/backend/src/main/java/com/suwasarana/api/camp/DonationCampController.java
git commit -m "feat(backend): expose slot booking rest endpoints and update security config"

git add apps/backend/src/main/java/com/suwasarana/api/scheduler/SlotReminderScheduler.java
git commit -m "feat(backend): add automated background reminders for slot bookings"

git add apps/frontend/package.json pnpm-lock.yaml apps/frontend/src/types/index.ts
git commit -m "feat(frontend): integrate qr code dependencies and base types"

git add apps/frontend/src/components/slots/SlotPicker.tsx apps/frontend/src/components/slots/MyBookings.tsx apps/frontend/src/app/camps/page.tsx
git commit -m "feat(frontend): create slot picker and bookings viewer components"

git add apps/frontend/src/components/slots/SlotManager.tsx
git commit -m "feat(frontend): implement staff slot manager with built-in qr scanner"

git add apps/frontend/src/app/dashboard/donor/page.tsx apps/frontend/src/app/dashboard/hospital/page.tsx apps/frontend/messages/en.json apps/frontend/messages/si.json apps/frontend/messages/ta.json .vscode/settings.json
git commit -m "feat(frontend): integrate slot components to dashboards and add i18n support"

git push
