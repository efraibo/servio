#!/usr/bin/env bash
# 🔵 Testa scripts/entregar.sh num repositório descartável, com verify e gh falsos.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

[ -x "$raiz/scripts/entregar.sh" ] || { echo "FALHOU: scripts/entregar.sh não existe" >&2; exit 1; }
git init -q --bare "$tmp/remoto.git"
git init -q -b main "$tmp/repo"
mkdir -p "$tmp/repo/scripts" "$tmp/bin"
cp "$raiz/scripts/entregar.sh" "$tmp/repo/scripts/"
cat > "$tmp/repo/verify" <<'F'
#!/bin/sh
echo "ruido-do-build"
echo "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0"
[ "${VERIFY_SAIDA:-0}" = 0 ] && echo "verify: OK"
exit "${VERIFY_SAIDA:-0}"
F
cat > "$tmp/bin/gh" <<'F'
#!/bin/sh
echo "$*" >> "$GH_CHAMADAS"
case "$1 $2" in
  "pr view") [ -f "$GH_CHAMADAS.pr" ] || exit 1; echo "https://exemplo/pr/1 OPEN" ;;
  "pr create") : > "$GH_CHAMADAS.pr"; echo "https://exemplo/pr/1" ;;
esac
F
chmod +x "$tmp/repo/verify" "$tmp/bin/gh"
cd "$tmp/repo"
git config user.email teste@exemplo.invalid && git config user.name teste
git add . && git commit -qm base && git remote add origin "$tmp/remoto.git" && git push -q origin main
export PATH="$tmp/bin:$PATH" GH_CHAMADAS="$tmp/gh.log"
echo corpo > "$tmp/corpo.md"

falha() { echo "FALHOU: $1" >&2; exit 1; }
entregar() { saida="$(scripts/entregar.sh "$@" 2>&1)" && codigo=0 || codigo=$?; }
limpa() { rm -f "$GH_CHAMADAS" "$GH_CHAMADAS.pr"; }
args=("feat(SRV-1): resumo" "SRV-1: titulo" "$tmp/corpo.md")

entregar "${args[@]}"
[ "$codigo" != 0 ] && grep -q "main" <<<"$saida" || falha "não recusou entregar da main"
echo "ok: recusa a main"

git switch -qc SRV-1-x
echo a > a; git add a; git commit -qm a
echo b > sujo
entregar "${args[@]}"
[ "$codigo" != 0 ] && [ ! -f "$GH_CHAMADAS" ] || falha "entregou com mudança não commitada"
rm sujo
echo "ok: recusa árvore suja"

limpa
VERIFY_SAIDA=1 entregar "${args[@]}"
[ "$codigo" != 0 ] && [ ! -f "$GH_CHAMADAS" ] || falha "seguiu com verify falhando"
git ls-remote --exit-code origin SRV-1-x >/dev/null 2>&1 && falha "fez push com verify falhando"
echo "ok: verify falhando não publica"

limpa
entregar "${args[@]}"
[ "$codigo" = 0 ] || falha "entrega normal: $saida"
git ls-remote --exit-code origin SRV-1-x >/dev/null || falha "sem push"
grep -q "ruido-do-build" <<<"$saida" && falha "saída do build não foi filtrada"
grep -q "Tests run: 3" <<<"$saida" || falha "sem resumo dos testes"
grep -q "^pr create --base main" "$GH_CHAMADAS" || falha "não criou o PR"
grep -q "^pr merge" "$GH_CHAMADAS" && falha "mergeou sem --auto-merge"
echo "ok: publica e abre o PR, sem merge"

limpa
entregar --auto-merge "${args[@]}"
[ "$codigo" = 0 ] || falha "auto-merge: $saida"
grep -q "^pr merge SRV-1-x --auto --squash --subject feat(SRV-1): resumo" "$GH_CHAMADAS" || falha "sem auto-merge squash"
grep -q -- "--admin" "$GH_CHAMADAS" && falha "usou --admin"
echo "ok: auto-merge só quando pedido, sem --admin"

limpa; : > "$GH_CHAMADAS.pr"
entregar "${args[@]}"
grep -q "^pr create" "$GH_CHAMADAS" && falha "criou um segundo PR"
echo "ok: reaproveita o PR aberto"
echo "PASSOU"
