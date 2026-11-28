#!/usr/bin/env bash
# 🔵 Cria o primeiro Gestor de uma oficina já criada (RN-18). Precisa do banco no ar, Java 25 e das
# variáveis de infra/.env. A senha é perguntada sem eco ou lida de SERVIO_GESTOR_SENHA:
#   set -a; . infra/.env; set +a
#   infra/provisionar-gestor.sh --slug=oficina-a --email=gestor@oficina-a.test
set -euo pipefail
cd "$(dirname "$0")/../backend"
# a senha não vai para o ambiente do Maven: compila sem ela e só o java a recebe
senha="${SERVIO_GESTOR_SENHA:-}"
unset SERVIO_GESTOR_SENHA
./mvnw -q -B -DskipTests package
if [ -z "$senha" ]; then
  IFS= read -rsp "Senha do Gestor: " senha
  echo
fi
SERVIO_GESTOR_SENHA="$senha" exec java -jar target/backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=provisionamento,gestor "$@"
