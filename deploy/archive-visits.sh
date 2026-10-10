#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
total=$(docker compose --env-file deploy/.env.vps -f deploy/compose.vps.yml exec -T backend cat /data/visits.txt)
case "$total" in ''|*[!0-9]*) echo 'Contagem inválida; arquivo não criado.' >&2; exit 1;; esac
mkdir -p records
stamp=$(date -u +%Y%m%dT%H%M%SZ)
record="records/visitas-$stamp.txt"
set -C
{
    printf 'Brasil Decide 2026 — visitas registradas: %s\n' "$total"
    printf 'Registro em UTC: %s\n' "$stamp"
    printf 'Contagem iniciada na instalação; inclui recarregamentos e pode incluir robôs. Não representa pessoas únicas.\n'
} > "$record"
printf 'Registro salvo em %s\n' "$record"
