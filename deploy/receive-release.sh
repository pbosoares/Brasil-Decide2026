#!/usr/bin/env bash
# Install root-owned at /opt/brasil-decide-2026/deploy/receive-release.sh.
# SSH key: restrict,command="/opt/brasil-decide-2026/deploy/receive-release.sh"
set -euo pipefail
umask 077
ROOT=/opt/brasil-decide-2026
if [[ ! ${SSH_ORIGINAL_COMMAND:-} =~ ^deploy\ ([a-f0-9]{40})$ ]]; then
  echo 'Comando de deploy inválido' >&2; exit 2
fi
release=${BASH_REMATCH[1]}
cd "$ROOT"
exec 9>deploy/deploy.lock
flock -w 180 9
test -f deploy/.env.vps
temporary=$(mktemp -d "$ROOT/deploy/release.XXXXXXXX")
compose=(docker compose --env-file deploy/.env.vps -f deploy/compose.vps.yml)
started=false
success=false
cleanup() {
  status=$?
  trap - EXIT
  if [[ $started == true && $success != true ]]; then
    echo 'Deploy falhou; restaurando as imagens anteriores' >&2
    if ! "${compose[@]}" -f "$temporary/previous.yml" up -d --no-build --no-deps --wait --wait-timeout 180 backend frontend; then
      echo 'ERRO: rollback requer intervenção' >&2
    fi
    install -m 600 "$temporary/previous.yml" deploy/release.override.yml || echo 'ERRO: não foi possível persistir rollback' >&2
    status=1
  fi
  rm -rf -- "$temporary"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Bound disk usage and refuse packages with tags outside this project's two services.
timeout 900 head -c 1073741825 > "$temporary/images.tar.gz"
test "$(stat -c %s "$temporary/images.tar.gz")" -le 1073741824
gzip -t "$temporary/images.tar.gz"
tar -xzOf "$temporary/images.tar.gz" manifest.json > "$temporary/manifest.json"
python3 - "$temporary/manifest.json" "$release" <<'PY'
import json, sys
with open(sys.argv[1]) as file:
    manifest = json.load(file)
expected = {f"brasil-decide-backend:{sys.argv[2]}", f"brasil-decide-frontend:{sys.argv[2]}"}
tags = [tag for image in manifest for tag in (image.get("RepoTags") or [])]
if len(manifest) != 2 or len(tags) != 2 or set(tags) != expected:
    raise SystemExit("Pacote contém imagens inesperadas")
PY

# Capture immutable image IDs before loading or replacing any running service.
printf 'services:\n' > "$temporary/previous.yml"
for service in backend frontend; do
  id=$("${compose[@]}" ps -q "$service")
  test -n "$id"
  image=$(docker inspect --format '{{.Image}}' "$id")
  [[ $image =~ ^sha256:[a-f0-9]{64}$ ]]
  printf '  %s:\n    image: %s\n    pull_policy: never\n' "$service" "$image" >> "$temporary/previous.yml"
done
gzip -dc "$temporary/images.tar.gz" | docker load
printf 'services:\n' > "$temporary/next.yml"
for service in backend frontend; do
  image="brasil-decide-$service:$release"
  test "$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")" = "$release"
  printf '  %s:\n    image: %s\n    pull_policy: never\n' "$service" "$image" >> "$temporary/next.yml"
done
"${compose[@]}" -f "$temporary/next.yml" config --quiet
started=true
"${compose[@]}" -f "$temporary/next.yml" up -d --no-build --no-deps --wait --wait-timeout 180 backend frontend
"${compose[@]}" exec -T backend wget -qO- http://127.0.0.1:8080/actuator/health | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
curl --retry 3 --retry-delay 2 --retry-all-errors --max-time 15 -fsS http://127.0.0.1:18081/healthz
curl --retry 3 --retry-delay 2 --retry-all-errors --max-time 15 -fsS https://brasildecide.pablo-soares.tech/ > /dev/null
install -m 600 "$temporary/previous.yml" deploy/rollback.override.yml
install -m 600 "$temporary/next.yml" deploy/release.override.yml
printf '%s\n' "$release" > deploy/deployed-release.txt
success=true
echo "Deploy concluído: $release"
