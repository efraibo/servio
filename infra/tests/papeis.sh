#!/usr/bin/env bash
# 🔵 SRV-26: sobe o compose num projeto descartável e confere os papéis no catálogo.
set -euo pipefail
cd "$(dirname "$0")/../.."

export POSTGRES_PASSWORD="teste-super-$RANDOM" SERVIO_OWNER_PASSWORD="teste-owner-$RANDOM" \
  SERVIO_APP_PASSWORD="teste-app-$RANDOM" POSTGRES_PORT=0
dc() { docker compose -p "servio-teste-$$" -f infra/compose.yaml "$@"; }
trap 'dc down -v >/dev/null 2>&1' EXIT
falha() { echo "FALHOU: $1" >&2; exit 1; }
confere() { [ "$2" = "$3" ] || falha "$1: esperado '$3', veio '$2'"; echo "ok: $1"; }
sql() { dc exec -T postgres psql -U postgres -d servio -Atc "$1"; }
# Conexão pelo IP do contêiner: em 127.0.0.1 a imagem usa trust e não confere senha.
login() { dc exec -T -e PGPASSWORD="$2" postgres sh -c "psql -h \"\$(hostname -i)\" -U $1 -d servio -Atc 'select current_user'"; }

dc up -d --wait >/dev/null 2>&1 || falha "compose não subiu"

confere "PostgreSQL 18" "$(sql "select current_setting('server_version_num')::int / 10000")" 18
confere "volume em /var/lib/postgresql" \
  "$(docker inspect -f '{{range .Mounts}}{{if eq .Type "volume"}}{{.Name}}:{{.Destination}}{{end}}{{end}}' "$(dc ps -q postgres)")" "servio-teste-$$_pgdata:/var/lib/postgresql"
for papel in servio_owner servio_app; do
  confere "$papel sem superuser, bypassrls, replication, createdb e createrole" \
    "$(sql "select rolsuper or rolbypassrls or rolreplication or rolcreatedb or rolcreaterole from pg_roles where rolname='$papel'")" f
done
confere "dono do schema public" "$(sql "select nspowner::regrole from pg_namespace where nspname='public'")" servio_owner
confere "app não é membro de nenhum papel" "$(sql "select count(*) from pg_auth_members where member='servio_app'::regrole")" 0
confere "dono do banco não é o app" "$(sql "select datdba::regrole from pg_database where datname='servio'")" postgres
confere "app usa o schema" "$(sql "select has_schema_privilege('servio_app','public','usage')")" t
confere "app não cria no schema" "$(sql "select has_schema_privilege('servio_app','public','create')")" f
confere "login do dono" "$(login servio_owner "$SERVIO_OWNER_PASSWORD")" servio_owner
confere "login do app" "$(login servio_app "$SERVIO_APP_PASSWORD")" servio_app
if login servio_app senha-errada >/dev/null 2>&1; then falha "senha errada foi aceita"; fi
echo "ok: senha errada é recusada"

exemplo=infra/.env.example
for v in POSTGRES_PASSWORD SERVIO_OWNER_PASSWORD SERVIO_APP_PASSWORD; do
  grep -q "^$v=ficticia" "$exemplo" || falha "$exemplo sem valor fictício para $v"
done
git check-ignore -q infra/.env || falha "infra/.env não está no .gitignore"
echo "ok: .env.example fictício e .env ignorado"
echo "PASSOU"
