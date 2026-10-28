@echo off
REM Run the Spring Boot backend locally with the `dev` profile (throw-away defaults + demo data).
REM Needs Postgres on localhost:5432 (docker compose up db) or set DB_URL / DB_USER / DB_PASSWORD yourself.
REM Secrets are NOT stored here: put real values (GROQ_API_KEY, ...) in your shell environment.
set SPRING_PROFILES_ACTIVE=dev
call mvnw.cmd spring-boot:run
