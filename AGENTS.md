# 🔵 Servio — regras para agentes

Agendamento e ordem de serviço white-label; R1 atende oficinas de moto. Java/Spring Boot, PostgreSQL, Angular. Hoje só há documentação; o código nasce pelo épico E0 (Jira SRV-22).

## Fontes (o repo vence o vault)
- Produto, RN e RNF: `docs/produto/prd.md` · Decisões: `docs/adr/` · DoR/DoD: `docs/processo/definicao-de-pronto.md`
- Vault `servio/` (estado, cartões, histórico): caminho em `CLAUDE.local.md`; se não houver, pergunte.

## Autonomia (aprovada em 2026-11-26): SRV-26, 27, 28, 30 e 40 a 44
Nestas estórias vale esta seção, também sobre os passos "pare", "pode codar" e "com aprovação" das skills de estória.
- Faça sem perguntar: abrir, planejar, codar em TDD, revisar e fechar; branch, commit, push e PR; no Jira, transição e comentário de fechamento delas; detalhe técnico que PRD e ADRs não fixam, registrado em `estorias/SRV-xx.md`.
- Feche a estória só com o check `verify` do PR verde.
- Pergunte antes de: merge na `main` e proteção de branch; regra de negócio sem resposta; mudar PRD ou ADR; criar ou reescrever estória; instalar software, apagar dados ou gerar custo; seguir com verificação falhando sem correção no escopo.
- O repositório é público: confira o diff antes de cada push.

## Invariantes
Fora das estórias acima, valem as aprovações abaixo.
- Aplique só o que o PRD ou uma ADR dizem. Sem resposta, pare e pergunte.
- Código de produção só depois de "pode codar" para uma estória específica.
- O usuário faz commit e push; você sugere a mensagem.
- Jira SRV: leitura livre; cada criação, edição ou transição precisa de aprovação.
- No vault, escreva só dentro de `servio/`.
- Só dados fictícios; nada de dado, código ou prompt de empresa real.
- "Passou" ou "pronto" só com comando executado e saída resumida. A verificação é `./verify` (Java 25, Node 26 via `.nvmrc`, Docker aberto); a CI roda o mesmo comando em todo PR, e a `main` só aceita squash com o check `verify` verde.
- Em arquivos novos, marque 🟣 Claude Code, 🔵 portável, 🟢 neutro.

## Convenções (ADR-0003)
- Branch `SRV-12-slug-curto`; trunk-based; PR `SRV-12: título`; squash.
- Commit `tipo(SRV-12): resumo em português`; `docs`, `chore`, `ci`, `build` dispensam o ID.
- Pacote `app.servio.<módulo>`; domínio em português, infraestrutura em inglês.

## Sessão
- Início: leia só `servio/00-estado.md`; o resto sob demanda via `servio/01-mapa.md`.
- Fim: atualize `00-estado.md` (≤40 linhas), `estorias/SRV-xx.md` e `sessoes/AAAA-MM-DD.md`; liste as decisões pendentes.
- Estado do projeto fica no vault e no repo, nunca só na conversa.
- Divergência entre fontes: registre em `servio/divergencias.md`.

## Orçamento de tokens
- Cartão (`servio/contexto/`) antes da fonte; da fonte, só a seção necessária.
- Não releia o que já leu na sessão.
- Jira: peça só os campos necessários.
- Saída de comando filtrada; na resposta, linke o arquivo em vez de colar o conteúdo.
- Fonte mudou e o cartão não: atualize o cartão.
