#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p run-logs

screen -S stockman-server -X quit >/dev/null 2>&1 || true
screen -S stockman-web -X quit >/dev/null 2>&1 || true

pids="$({ lsof -ti tcp:8080; lsof -ti tcp:8081; } 2>/dev/null | sort -u || true)"
if [[ -n "$pids" ]]; then
  kill $pids 2>/dev/null || true
  sleep 2
fi

./gradlew --no-daemon :server:installDist > run-logs/server-build.log 2>&1
screen -dmS stockman-server bash -lc "cd '$ROOT_DIR' && server/build/install/server/bin/server > run-logs/server.log 2>&1"
screen -dmS stockman-web bash -lc "cd '$ROOT_DIR' && ./gradlew --no-daemon -Dkotlin.daemon.jvm.options=-Xmx2g :web:jsBrowserDevelopmentRun > run-logs/web.log 2>&1"

echo "server: http://localhost:8080"
echo "db:     http://localhost:8080/db"
echo "web:    http://localhost:8081"
echo "logs:   run-logs/server-build.log run-logs/server.log run-logs/web.log"
