#!/usr/bin/env bash
set -euo pipefail
: "${RELEASE:?Release ausente}"
cleanup() {
  docker rm -f ci-brasil-backend ci-brasil-frontend >/dev/null 2>&1 || true
  docker volume rm ci-brasil-data >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker volume create ci-brasil-data
docker run -d --name ci-brasil-backend --read-only --tmpfs /tmp -v ci-brasil-data:/data \
  --cap-drop ALL --security-opt no-new-privileges -e TSE_ENABLED=false -e R2_ENABLED=false \
  -p 127.0.0.1:18080:8080 "brasil-decide-backend:$RELEASE"
docker run -d --name ci-brasil-frontend --read-only --tmpfs /tmp:uid=101,gid=101 \
  --cap-drop ALL --security-opt no-new-privileges -p 127.0.0.1:18081:8080 "brasil-decide-frontend:$RELEASE"
wait_backend() {
  for attempt in $(seq 1 90); do
    if curl -fsS http://127.0.0.1:18080/actuator/health > /tmp/backend-health.json 2>/dev/null; then return; fi
    sleep 1
  done
  docker logs ci-brasil-backend >&2
  return 1
}
wait_backend
curl -fsS http://127.0.0.1:18080/actuator/health | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
curl -fsS http://127.0.0.1:18081/healthz
curl -fsS http://127.0.0.1:18081/brazil-flag.svg | grep -q '<svg'
curl -fsS http://127.0.0.1:18080/api/v1/results/manifest.json | python3 -c 'import json,sys; m=json.load(sys.stdin); assert m["versao"] is None and m["arquivo"] is None'
test "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/api/v1/results/national)" = 503
test "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18081/results/snapshots/missing.json)" = 404
docker exec ci-brasil-frontend nginx -t
curl -fsS -H 'Origin: https://brasildecide.pablo-soares.tech' -H 'Content-Type: application/json' \
  -d '{"id":"12345678-1234-4123-8123-123456789012"}' http://127.0.0.1:18080/api/v1/visits \
  | python3 -c 'import json,sys; assert json.load(sys.stdin)["visitas"] == 1'
docker restart ci-brasil-backend
wait_backend
curl -fsS http://127.0.0.1:18080/api/v1/visits | python3 -c 'import json,sys; assert json.load(sys.stdin)["visitas"] == 1'
