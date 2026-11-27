@AGENTS.md

## 🟣 Claude Code
- `/iniciar` abre a sessão e `/encerrar` fecha; entre estórias o usuário roda `/clear`.
- Estórias: skills `abrir-estoria`, `planejar-estoria`, `fechar-estoria`. TDD, revisão e PR: skills globais.
- Leitura em massa do Jira: agente `leitor-jira`. Revisão da DoD: agente `revisor`.
- Não use a memória automática para estado do projeto.
- Hooks ficam para depois do `./verify`.
