---
name: abrir-estoria
description: Abrir uma estória SRV-xx (ler do Jira, checar a DoR, criar o arquivo no vault). Usar quando o usuário pedir para começar ou pegar uma estória.
---
🟣
1. Leia a issue no Jira só com título, descrição, status, pai, labels e links.
2. Confira cada item da DoR (cartão `contexto/dor-dod.md`). Item não atendido ou decisão em aberto: pare e mostre ao usuário.
3. Crie `estorias/SRV-xx.md` a partir de `estorias/_modelo.md`.
4. Sugira a branch `SRV-xx-slug`. Transição no Jira só com aprovação.
5. Siga para a skill `planejar-estoria`.
