#!/usr/bin/env bash
# 🔵 Cria o primeiro Gestor de uma oficina já criada (RN-18). Precisa do banco no ar, Java 25 e das
# variáveis de infra/.env. A senha é perguntada sem eco ou lida de SERVIO_GESTOR_SENHA:
#   set -a; . infra/.env; set +a
#   infra/provisionar-gestor.sh --slug=oficina-a --email=gestor@oficina-a.test
set -euo pipefail
cd "$(dirname "$0")/../backend"
if [ -z "${SERVIO_GESTOR_SENHA:-}" ]; then
  IFS= read -rsp "Senha do Gestor: " SERVIO_GESTOR_SENHA
  echo
fi
export SERVIO_GESTOR_SENHA
./mvnw -q -B -DskipTests package
exec java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=provisionamento,gestor "$@"
