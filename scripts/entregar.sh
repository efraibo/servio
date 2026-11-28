#!/usr/bin/env bash
# 🔵 Entrega a branch atual: verify resumido, push, PR e, se pedido, auto-merge do GitHub.
# Não espera a CI: com --auto-merge, o GitHub faz o squash quando o check obrigatório passar.
set -euo pipefail
cd "$(dirname "$0")/.."

uso() { echo 'uso: scripts/entregar.sh [--auto-merge] "<assunto do commit>" "<título do PR>" <arquivo do corpo>'; }
erro() { echo "entregar: $1" >&2; exit 1; }

auto=0
[ "${1:-}" = "--auto-merge" ] && { auto=1; shift; }
[ $# = 3 ] || { uso >&2; exit 2; }
assunto="$1" titulo="$2" corpo="$3"

branch="$(git branch --show-current)"
[ -n "$branch" ] && [ "$branch" != main ] || erro "não se entrega a partir da main"
[ -z "$(git status --porcelain)" ] || erro "há mudanças não commitadas"
[ -f "$corpo" ] || erro "corpo do PR não encontrado: $corpo"

log="$(mktemp)"
if ! ./verify >"$log" 2>&1; then
  grep -E "Tests run:|ERROR|FAIL|verify:" "$log" | tail -20 >&2 || tail -20 "$log" >&2
  erro "verify falhou; log completo em $log"
fi
grep -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$|verify:" "$log" || true

git push -q -u origin "$branch"
if ! gh pr view "$branch" --json url,state >/dev/null 2>&1; then
  gh pr create --base main --head "$branch" --title "$titulo" --body-file "$corpo"
fi
if [ "$auto" = 1 ]; then
  gh pr merge "$branch" --auto --squash --subject "$assunto" \
    --body "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
fi
gh pr view "$branch" --json url,state -q '.url + " " + .state'
