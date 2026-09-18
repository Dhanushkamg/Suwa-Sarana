@echo off
REM Run Spring Boot backend
set DB_PASSWORD=postgres123
set GROQ_API_KEY=gsk_YOUR_GROQ_API_KEY_HERE
call mvnw.cmd clean spring-boot:run
