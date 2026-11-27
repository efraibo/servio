#!/usr/bin/env bash
# 🔵 SRV-28: testa o ./verify num repositório falso, com java, node e mvnw falsos.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unset JAVA_HOME

mkdir -p "$tmp/bin" "$tmp/repo/backend" "$tmp/repo/frontend"
cp "$raiz/verify" "$tmp/repo/verify" 2>/dev/null || { echo "FALHOU: ./verify não existe" >&2; exit 1; }
cat > "$tmp/bin/java" <<'F'
#!/bin/sh
echo "openjdk version \"${JAVA_FALSO:-25.0.2}\" 2026-01-20" >&2
F
cat > "$tmp/bin/node" <<'F'
#!/bin/sh
echo "v${NODE_FALSO:-26.1.0}"
F
cat > "$tmp/repo/backend/mvnw" <<'F'
#!/bin/sh
echo "$*" >> "$(dirname "$0")/chamadas"
exit "${MVNW_SAIDA:-0}"
F
chmod +x "$tmp/bin/java" "$tmp/bin/node" "$tmp/repo/backend/mvnw"
export PATH="$tmp/bin:$PATH"

# caso <nome> <código esperado> <mvnw chamado: sim|nao> <trecho esperado> [VAR=valor...] -- [args]
caso() {
  local nome="$1" codigo="$2" chamou="$3" trecho="$4" saida obtido=0 ambiente=()
  shift 4
  while [ "$1" != "--" ]; do ambiente+=("$1"); shift; done
  shift
  rm -f "$tmp/repo/backend/chamadas"
  # roda de fora do repositório: o script não pode depender do diretório atual
  saida="$(cd "$tmp" && env ${ambiente[@]+"${ambiente[@]}"} "$tmp/repo/verify" "$@" 2>&1)" || obtido=$?
  [ "$obtido" = "$codigo" ] || { echo "FALHOU: $nome: saída $obtido, esperada $codigo: $saida" >&2; exit 1; }
  local real=nao
  [ -f "$tmp/repo/backend/chamadas" ] && real=sim
  [ "$real" = "$chamou" ] || { echo "FALHOU: $nome: mvnw chamado=$real" >&2; exit 1; }
  grep -qF -- "$trecho" <<<"$saida" || { echo "FALHOU: $nome: sem '$trecho' em: $saida" >&2; exit 1; }
  echo "ok: $nome"
}

caso "sucesso, front pulado" 0 sim "frontend/ sem projeto Angular" --
grep -qx -- "-B clean verify" "$tmp/repo/backend/chamadas" || { echo "FALHOU: mvnw não rodou exatamente '-B clean verify'" >&2; exit 1; }
# front antes do back
ordem="$(cd "$tmp" && "$tmp/repo/verify" 2>&1 | grep '^==' | tr '\n' ' ')"
[ "$ordem" = "== frontend == backend " ] || { echo "FALHOU: ordem '$ordem'" >&2; exit 1; }
caso "falha do backend" 7 sim "" MVNW_SAIDA=7 --
caso "Java errado" 1 nao "Java 25" JAVA_FALSO=21.0.1 --
mkdir -p "$tmp/jdk/bin"
printf '#!/bin/sh\necho "openjdk version \\"21.0.1\\"" >&2\n' > "$tmp/jdk/bin/java"; chmod +x "$tmp/jdk/bin/java"
caso "JAVA_HOME vale mais que o PATH" 1 nao "encontrado: 21" JAVA_HOME="$tmp/jdk" --
caso "Node errado" 1 nao "Node 26" NODE_FALSO=23.5.0 --
caso "--back" 0 sim "OK" -- --back
if (cd "$tmp" && "$tmp/repo/verify" --back 2>&1) | grep -qi frontend; then echo "FALHOU: --back rodou o front" >&2; exit 1; fi
caso "--front" 0 nao "frontend/ sem projeto Angular" -- --front
caso "--help" 0 nao "uso:" -- --help
caso "argumento desconhecido" 2 nao "uso:" -- --tudo
caso "argumentos demais" 2 nao "uso:" -- --back --front
touch "$tmp/repo/frontend/angular.json"
caso "front ainda sem integração não passa em silêncio" 1 nao "SRV-32" -- --front
echo "PASSOU"
