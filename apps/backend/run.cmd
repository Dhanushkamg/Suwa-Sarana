@echo off
REM Run Spring Boot backend
set DB_PASSWORD=postgres123
call mvnw.cmd clean spring-boot:run
