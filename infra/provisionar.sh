#!/usr/bin/env bash
# 🔵 Cria uma oficina (RN-18). Precisa do banco no ar, Java 25 e das variáveis de infra/.env:
#   set -a; . infra/.env; set +a
#   infra/provisionar.sh --nome="Oficina A" --slug=oficina-a --cor-primaria="#1A73E8" --cor-secundaria="#F4B400"
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -q -B -DskipTests package
exec java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=provisionamento "$@"
