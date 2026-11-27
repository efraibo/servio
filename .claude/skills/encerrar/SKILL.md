---
name: encerrar
description: Fechar a sessão do Servio gravando estado, estória e log.
disable-model-invocation: true
---
🟣 Caminhos relativos ao vault `servio/`.
1. Reescreva `00-estado.md` (≤40 linhas): onde parei, próxima ação, bloqueios, decisões pendentes. Histórico não fica aqui.
2. Se houve estória, atualize `estorias/SRV-xx.md`.
3. Acrescente a `sessoes/AAAA-MM-DD.md`: feito e o que ficou, em poucas linhas.
4. Atualize `metricas.md` e `divergencias.md` se houver algo novo.
5. Pronto quando alguém sem esta conversa souber a próxima ação só pelo estado. Liste as decisões pendentes na resposta.
