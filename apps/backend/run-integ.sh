#!/usr/bin/env bash
# Starts the packaged backend (target/backend-0.0.1-SNAPSHOT.jar) against the scratch database `suwa_integ` on the dev
# Postgres container (docker start suwa-pg-dev -> localhost:55432). Dev profile = demo data is seeded on first start.
# Usage (Git Bash, from apps/backend):   ./run-integ.sh            (foreground; Ctrl+C stops it)
# Secrets are generated once into .integ-secrets (git-ignored) so restarts keep the same signing keys.
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${SUWA_JAVA_HOME:-C:/Users/Kalana/.jdks/corretto-21.0.8}"
SECRETS=.integ-secrets
if [ ! -f "$SECRETS" ]; then
  {
    echo "JWT_SECRET=$(head -c 64 /dev/urandom | base64 -w0)"
    echo "AUTH_HMAC_SECRET=$(head -c 36 /dev/urandom | base64 -w0 | tr -d '/+=' | head -c 40)"
  } > "$SECRETS"
fi
set -a; . "./$SECRETS"; set +a

# 8080 is taken on this machine by an Apache/WSL listener, so the backend runs on 8081 (override with PORT=...).
export PORT="${PORT:-8081}"
export SPRING_PROFILES_ACTIVE=dev
export DB_URL=jdbc:postgresql://localhost:55432/suwa_integ
export DB_USER=postgres
export DB_PASSWORD=devonly123
export CORS_ALLOWED_ORIGIN=http://localhost:3000
export FRONTEND_URL=http://localhost:3000
export PUBLIC_BASE_URL="http://localhost:${PORT}"
export DEMO_PASSWORD='Demo-Pass-12345'

exec "$JAVA_HOME/bin/java" -Xmx512m -jar target/backend-0.0.1-SNAPSHOT.jar
