# Servio

Plataforma white-label de agendamento e ordem de serviço para negócios de serviço. A R1 atende oficinas de motos (gestão: agenda, cadastros, OS, painel do dia). Backend Java com Spring Boot e PostgreSQL; frontend Angular. Hoje o repositório só tem documentação; o código nasce pelo épico E0 (Jira SRV-22).

## Fontes de verdade

- Produto e regras de negócio (RN-xx, RNF-xx): `docs/produto/prd.md`
- Quando uma estória está pronta (DoR) e quando está feita (DoD): `docs/processo/definicao-de-pronto.md`
- Decisões técnicas: `docs/adr/` (0001 multi-tenant com RLS, 0002 autenticação e tenant, 0003 estrutura do projeto)

## Regra de negócio

Aplique só o que o PRD ou uma ADR dizem. Se nenhum dos dois responder, pare e pergunte antes de seguir.

## Convenções (ADR-0003)

- Branch: `SRV-12-slug-curto`, curta, uma por estória; trunk-based, `main` protegida.
- Commit: `tipo(SRV-12): resumo em português`. Tipos: `feat`, `fix`, `test`, `refactor` (levam `SRV-xx`) e `docs`, `chore`, `ci`, `build` (dispensam o ID).
- PR: título `SRV-12: título`, squash merge.
- Backend: pacote raiz `app.servio`, um subpacote por módulo (`tenant`, `acesso`, `cadastros`, `agenda`, `os`, `painel`), por exemplo `app.servio.acesso`.
- Identificadores: domínio em português (`Autenticador`), infraestrutura em inglês (`TenantContext`).

## Verificação

O comando único será `./verify` (estória SRV-28). Ele ainda não existe: até lá, nada verifica o código sozinho. Ao concluir uma tarefa de código, diga que a verificação automática não existe em vez de afirmar que passou.

## Git

Faça commit e push só quando o usuário pedir.
