# syntax=docker/dockerfile:1.7
#
# Multi-stage build: JDK + Maven build stage -> slim JRE runtime stage, non-root, health-checked.
# Image tags are pinned (update them deliberately, e.g. via Dependabot's docker ecosystem), never "latest"/floating.
ARG JDK_IMAGE=eclipse-temurin:21.0.12_8-jdk-alpine-3.23
ARG JRE_IMAGE=eclipse-temurin:21.0.12_8-jre-alpine-3.23

# ---------------------------------------------------------------- build
FROM ${JDK_IMAGE} AS builder
WORKDIR /build

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN ./mvnw -B -q clean package -DskipTests

# ---------------------------------------------------------------- runtime
FROM ${JRE_IMAGE} AS runtime
WORKDIR /app

# Fixed numeric uid/gid so Kubernetes/ECS "runAsNonRoot" policies can verify it.
RUN addgroup -S -g 10001 spring && adduser -S -u 10001 -G spring -H -s /sbin/nologin spring

COPY --from=builder --chown=10001:10001 /build/target/*.jar /app/app.jar

USER 10001:10001

# prod profile by default: strict start-up validation + JSON logs. docker-compose overrides it with `dev`.
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Readiness includes the database, so an unhealthy container is also one that cannot serve requests.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider "http://127.0.0.1:${PORT:-8080}/actuator/health/readiness" || exit 1

# exec: java becomes PID 1 and receives SIGTERM directly -> graceful shutdown (server.shutdown=graceful).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
