---
name: fechar-estoria
description: Fechar uma estória SRV-xx com DoD e evidência. Usar quando a implementação parecer concluída ou o usuário pedir para fechar.
---
🟣
1. Rode os testes (`./verify` quando existir) e grave comando + saída resumida em `estorias/SRV-xx.md`.
2. Percorra cada critério de aceite e cada item da DoD (cartão `contexto/dor-dod.md`): atendido com evidência, ou pendente com o motivo.
3. Peça a revisão ao agente `revisor`; regra de negócio também precisa de revisão do usuário.
4. Sugira mensagem de commit e título de PR. Commit, push e Jira ficam com o usuário ou com a aprovação dele.
5. Registre tempo e retrabalho em `metricas.md`.
