# Data retention

How long Suwa Sarana keeps personal data, why, and how it is removed. The periods are the defaults of
`app.retention.*` (class `RetentionProperties`); changing a property changes the behaviour, so keep this table in sync.
The jobs live in `scheduler/DataRetentionScheduler`, run on Sri Lanka time (`Asia/Colombo`) and are guarded by a
PostgreSQL advisory lock so that several instances never run the same job at once. A period of `0` switches that purge off.

## What is purged

| Data (table / columns) | Purpose | Retention | Deletion method | Property (default) |
|---|---|---|---|---|
| Refresh tokens (`refresh_tokens`) | Keep a user signed in | Until expiry (7 days) + 1 day grace | Row deleted, daily 02:00 | `refresh-token-grace` (`1d`) |
| OTP checks (`otp_verifications`: phone number, code, attempts) | Prove phone ownership at sign-up / password reset | 1 day after creation | Row deleted, hourly (minute 15) | `otp-retention` (`1d`) |
| Contact-form messages, handled (`contact_messages`: name, e-mail, subject, text) | Answer enquiries | 180 days after an administrator marked the message handled | Row deleted, daily | `contact-handled-days` (`180`) |
| Contact-form messages, never handled | Same; stops forgotten messages living for ever | 365 days after receipt | Row deleted, daily | `contact-unhandled-days` (`365`) |
| Notification delivery logs (`notification_logs.recipient`, `.message_body`: phone number, SMS text) | Prove an alert was attempted; debugging delivery | 90 days after sending | The two columns are set to `NULL`; the row (donor, request, channel, status, time) stays for statistics | `notification-log-scrub-days` (`90`) |
| Circle pledges (`circle_responses`: name, phone, notes of friends/family who volunteered through an invite link) | Let the requester contact volunteers | 30 days after the request closed (cancelled / fulfilled / expired), or 30 days after the circle's own expiry, whichever the data meets first | Name becomes `[removed]`, phone becomes empty, notes `NULL`, `scrubbed_at` set; the row (blood type, time) stays so counts do not change | `circle-response-scrub-days` (`30`), `closed-statuses` (`CANCELLED,FULFILLED,EXPIRED`) |
| Circle invite tokens (`request_circles.invite_token_hash`, `.invite_token_enc`) | Private sharing link of one request | Valid only while the request is active (see below); the row is deleted with the request | Cascade delete with the request | `app.circle.active-statuses` (`OPEN,ESCALATING,MATCHED`) |
| Blood requests that ended without being fulfilled (`blood_requests` and, by cascade, matches, notification logs, circles, pledges, reports) | Operations and abuse review | 30 days after the request's expiry time, for the statuses listed | Row deleted; database `ON DELETE CASCADE` removes the children | `request-purge-days` (`30`), `request-purge-statuses` (`EXPIRED,CANCELLED`) |

`FULFILLED` requests are deliberately kept: they back the donation history and the national statistics. A request that is
stuck in `MATCHED` after its expiry is a lifecycle matter (it should become `EXPIRED` or `FULFILLED`); until the request
lifecycle does that, the circle pledges of such a request are still scrubbed (circle expiry rule above), and an operator
can add `MATCHED` to `request-purge-statuses` to delete them.

## Not handled by these jobs (decisions for the owner)

These hold personal data too but are outside the abuse / scheduler scope of this change:

* `users`, `donor_profiles` (identity, exact home coordinates, NIC), `donor_deferrals`: kept while the account exists; account
  deletion / export is a separate feature (finding F-23).
* `slot_bookings`, `donation_slots`: kept as donation history.
* `request_matches`: removed with their request; kept for fulfilled requests.
* Application logs: not stored by the application; set the retention of the log destination (recommended 30 days).
* Database backups: follow the backup policy of the hosting account; a deleted row can survive in a backup until it ages out.

## Behaviour guarantees

* Every purge is one idempotent SQL statement; a failing purge is logged (class of the error only) and the next one still runs.
* Only counts are logged. No personal data is written to the log by the jobs.
* Circle invite links stop working as soon as the request is cancelled, fulfilled or expired, long before the pledge data is scrubbed.
* The retention cut-offs are computed from an injected clock (`Asia/Colombo`), so tests use a fixed clock.

## Operating notes

* Environment / properties: `APP_RETENTION_*` (e.g. `APP_RETENTION_CONTACT_HANDLED_DAYS=90`), `app.retention.cron`
  (default `0 0 2 * * *`), `app.retention.otp-cron` (default `0 15 * * * *`), `app.scheduler.lock-enabled` (default `true`).
* After the first deployment of migration V43 the indexes on `refresh_tokens(expires_at)` and `notification_logs(sent_at)`
  make the purges cheap; on very large tables run the first purge outside peak hours.
