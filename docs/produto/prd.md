---
documento: PRD
produto: Servio
release: R1 (MVP — Gestão)
versao: 1.3
status: Aprovado (2026-11-19)
autor: Evandro Nascimento (PO/TL) com apoio de IA
criado: 2026-11-19
---

# PRD — Servio R1: Gestão de atendimentos para oficinas

> **Status: aprovado em 19/11/2026 pelo PO.** Mudanças a partir daqui entram como nova versão no histórico.

## 1. Contexto

O **Servio** é uma plataforma white-label de agendamento e ordem de serviço para negócios de serviço (oficinas, salões, pet shops, assistências técnicas). Cada negócio usa o sistema com a própria marca, e o que muda por setor fica em configuração.

A R1 atende o primeiro segmento: **oficinas de motos**. O domínio vem do projeto anterior MotoWorkshop, usado apenas como referência.

## 2. Problema

Oficinas pequenas e médias controlam agenda e serviços em caderno, WhatsApp e planilha. Na prática:

- o dono não sabe, em tempo real, quais motos estão na oficina e em que etapa;
- horários são marcados em conflito ou esquecidos;
- o cliente liga para perguntar "minha moto está pronta?", e ninguém sabe responder sem ir ao box;
- o histórico de serviços de cada moto se perde, e a oficina não consegue oferecer a próxima revisão.

## 3. Objetivos da R1

| # | Objetivo | Como medir (no próprio sistema) | Meta R1 |
|---|---|---|---|
| O1 | Dar visibilidade do que está na oficina agora | % de OS abertas com status atualizado nas últimas 24h | ≥ 90% |
| O2 | Organizar a agenda | Nº de agendamentos com conflito de capacidade | 0 (o sistema impede) |
| O3 | Registrar o histórico de cada moto | % de OS concluídas vinculadas a cliente e moto | 100% |
| O4 | Ser rápido no balcão | Tempo para abrir uma OS de um cliente já cadastrado | ≤ 1 minuto |

**Objetivo do projeto (não do produto):** praticar o processo completo de empresa com IA, da concepção ao deploy. Métricas de processo ficam fora deste PRD.

## 4. Personas

| Persona | Quem é | O que precisa | Dispositivo principal |
|---|---|---|---|
| **Dono/Gestor** (admin do tenant) | Dono da oficina | Visão do dia, cadastro de serviços e equipe, configurações da marca | Web (computador) e celular |
| **Atendente** | Recebe clientes e telefonemas | Agendar, cadastrar cliente e moto, abrir OS rápido | Web (balcão) |
| **Mecânico** | Executa os serviços | Ver as OS dele e atualizar status e fotos pelo celular | Celular |
| Cliente final | Dono da moto | Agendar e acompanhar a OS | **R2** (portal) — fora desta release |

## 5. Escopo da R1

Prioridade MoSCoW: **M** = obrigatório, **S** = deveria ter, **C** = se der tempo.

### Épico E1 — Plataforma e acesso
| ID | Feature | Prioridade |
|---|---|---|
| E1.1 | Tenant (oficina) com nome, logo e cores básicas | M |
| E1.2 | Login com e-mail e senha | M |
| E1.3 | Perfis: Gestor, Atendente, Mecânico | M |
| E1.4 | Gestor convida e desativa usuários da equipe | S |
| E1.5 | Recuperação de senha | S |
| E1.6 | Sair do sistema (logout), encerrando a sessão no servidor | M |

### Épico E2 — Cadastros
| ID | Feature | Prioridade |
|---|---|---|
| E2.1 | Clientes: cadastrar, editar, buscar por nome ou telefone | M |
| E2.2 | Motos (item atendido): várias por cliente, com placa, marca, modelo, ano e km | M |
| E2.3 | Catálogo de serviços: nome, duração estimada, preço base, ativo/inativo | M |
| E2.4 | Histórico de OS por moto | S |

### Épico E3 — Agenda
| ID | Feature | Prioridade |
|---|---|---|
| E3.1 | Criar agendamento: cliente, moto, serviços, data e hora | M |
| E3.2 | Ver agenda do dia e da semana | M |
| E3.3 | Impedir agendamento acima da capacidade do horário | M |
| E3.4 | Reagendar e cancelar, com motivo | M |
| E3.5 | Configurar horário de funcionamento e capacidade | M |

### Épico E4 — Ordem de serviço
| ID | Feature | Prioridade |
|---|---|---|
| E4.1 | Abrir OS a partir de um agendamento ou direto no balcão | M |
| E4.2 | Itens da OS: serviços do catálogo, com preço ajustável | M |
| E4.3 | Fluxo de status (ver RN-07) com registro de quem mudou e quando | M |
| E4.4 | Atribuir OS a um mecânico | M |
| E4.5 | Mecânico vê "minhas OS" e muda status pelo celular | M |
| E4.6 | Fotos da moto na entrada e na saída | S |
| E4.7 | Observações e km de entrada | S |
| E4.8 | Imprimir ou gerar PDF da OS | C |

### Épico E5 — Painel do dia
| ID | Feature | Prioridade |
|---|---|---|
| E5.1 | Agenda de hoje e OS agrupadas por status | M |
| E5.2 | Indicadores simples: OS abertas, concluídas no dia, atrasadas | S |

## 6. Fora do escopo da R1

- Portal do cliente final, agendamento online e acompanhamento pelo cliente (**R2**)
- Orçamento com aprovação do cliente e notificações por WhatsApp ou e-mail (**R3**)
- Segundo segmento (salão etc.) (**R3**)
- Estoque de peças, financeiro, pagamentos, nota fiscal
- App nas lojas (iOS/Android), multi-idioma, configuração low-code

## 7. Regras de negócio

| ID | Regra |
|---|---|
| RN-01 | Todo dado pertence a um tenant. Um usuário nunca vê dados de outro tenant. |
| RN-02 | Telefone do cliente é obrigatório. E-mail e CPF são opcionais (dados mínimos, LGPD). |
| RN-03 | Placa é única por tenant. A mesma placa pode existir em tenants diferentes. |
| RN-04 | Um cliente pode ter várias motos. Uma moto pertence a um cliente por vez. |
| RN-05 | Serviço inativo não aparece para novos agendamentos ou OS, mas continua nas OS antigas. |
| RN-06 | Capacidade: número máximo de atendimentos simultâneos por faixa de horário, configurado pelo gestor. |
| RN-07 | Status da OS: `Aberta → Em execução ⇄ Aguardando peça → Concluída → Entregue`. `Cancelada` só a partir de `Aberta` ou `Em execução`, com motivo. |
| RN-08 | O mecânico só altera status das OS atribuídas a ele. Gestor e atendente alteram qualquer OS. |
| RN-09 | O preço na OS é copiado do catálogo no momento da inclusão. Mudar o catálogo não altera OS existentes. |
| RN-10 | OS `Entregue` ou `Cancelada` não pode mais ser editada. |
| RN-11 | Status do agendamento: `Agendado → Em atendimento (vira OS) → Concluído`, ou `Cancelado` / `Não compareceu`. |
| RN-12 | Telefone: celular brasileiro com DDD (10 ou 11 dígitos), armazenado só com números. Telefone duplicado entre clientes é permitido, com aviso na tela. |
| RN-13 | Moto: placa, marca e modelo obrigatórios; ano e km opcionais. Placa no formato antigo (`ABC1234`) ou Mercosul (`ABC1D23`), armazenada em maiúsculas e sem hífen. Ano entre 1950 e o ano atual + 1. Km maior ou igual a 0. |
| RN-14 | Transferir moto para outro cliente não é permitido na R1. |
| RN-15 | Busca de cliente: por trecho do nome (ignorando maiúsculas e acentos) ou por trecho do telefone (só números). |
| RN-16 | Serviço: duração em minutos, maior que 0 e até 480; preço base maior ou igual a 0 (zero permitido para cortesia); nome único por tenant entre os serviços ativos. |
| RN-17 | Marca do tenant: cor primária e secundária em hexadecimal; logo PNG ou SVG com até 500 KB. O armazenamento do logo é definido em ADR. |
| RN-18 | Provisionamento: o primeiro tenant e o primeiro Gestor são criados por script de provisionamento. Não há tela de administração da plataforma na R1. |
| RN-19 | Autenticação (valores de referência para a ADR): sessão expira em 8h; senha com no mínimo 8 caracteres; bloqueio de 15 minutos após 5 tentativas inválidas seguidas. |
| RN-20 | Falha de login mostra sempre a mesma mensagem: "E-mail ou senha inválidos. Após 5 tentativas, o acesso fica bloqueado por 15 minutos." A mensagem não muda quando a conta está bloqueada, para não revelar o estado da conta. |
| RN-21 | O slug da oficina é público (aparece no link compartilhado com a equipe). Slug inexistente mostra a tela de login neutra, sem nome nem cores, e o envio retorna o mesmo erro genérico. |
| RN-22 | O e-mail do usuário é único por oficina, sem diferenciar maiúsculas e minúsculas, e é armazenado em minúsculas. O mesmo e-mail pode existir em oficinas diferentes. |
| RN-23 | O script de provisionamento do primeiro Gestor falha sem alterar nada quando a oficina já tem um Gestor ativo, informando o motivo. Ele nunca altera a senha de um usuário existente. |

### Matriz de permissões (R1)

| Recurso | Gestor | Atendente | Mecânico |
|---|---|---|---|
| Equipe (usuários) | Gerencia | — | — |
| Catálogo de serviços | Gerencia | Consulta | Consulta |
| Clientes e motos | Gerencia | Gerencia | Consulta |
| Agenda | Gerencia | Gerencia | Consulta a do dia |
| Ordens de serviço | Gerencia todas | Gerencia todas | Consulta e altera status só das atribuídas a ele (RN-08) |
| Painel do dia | Vê | Vê | — |

## 8. Requisitos não funcionais

| ID | Categoria | Requisito |
|---|---|---|
| RNF-01 | Multi-tenant | Isolamento garantido no banco, não só na aplicação (estratégia definida em ADR) |
| RNF-02 | Mobile | Telas mobile-first; app instalável como PWA |
| RNF-03 | White-label | Cores e logo do tenant aplicados via design tokens, sem build por cliente |
| RNF-04 | Segurança | OWASP Top 10 como checklist de revisão; senhas com hash forte; sessão com expiração |
| RNF-05 | LGPD | Coleta mínima; exclusão ou anonimização do cliente sob pedido; dados fictícios em dev e testes |
| RNF-06 | Auditoria | Mudança de status registra usuário e data |
| RNF-07 | Desempenho | Telas principais respondem em menos de 2s com até 5.000 OS por tenant |
| RNF-08 | Qualidade | Testes automatizados nos critérios de aceite; CI verde obrigatório para merge |
| RNF-09 | Observabilidade | Logs estruturados com `tenantId` e health check |
| RNF-10 | Acessibilidade | Contraste e navegação por teclado no nível básico (WCAG AA como referência) |

## 9. Premissas e dependências

- Stack: Java + Spring Boot, PostgreSQL, Angular (workspace com `gestao` e `portal`), detalhada em ADRs.
- Hospedagem em cloud gratuita (Oracle Always Free como primeira opção).
- Um único desenvolvedor, cerca de 3h por semana de projeto: o escopo M precisa caber em ~2 meses.

## 10. Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Escopo crescer (repetir o projeto anterior) | Alto | MoSCoW rígido; qualquer item novo entra como C ou vai para R2+ |
| Abstração "genérica" atrasar a R1 | Médio | Genérico só no modelo de dados (item atendido); UI e termos de oficina na R1 |
| Agente gerar código fora do padrão | Médio | Critérios de aceite verificáveis, hooks de teste, revisão em camadas |
| Limite da cloud gratuita | Baixo | Docker Compose enxuto; plano B documentado em ADR |

## 11. Decisões tomadas na aprovação

Decisões padrão adotadas na aprovação, revisáveis em versões futuras:

| # | Pergunta | Decisão R1 |
|---|---|---|
| D1 | Capacidade da agenda | Só por faixa de horário. Por mecânico ou box fica para R3+ |
| D2 | Tempo gasto pelo mecânico | Fora da R1 |
| D3 | Fotos da OS (E4.6) | Opcionais |
| D4 | Configuração da marca | Feita no cadastro do tenant (sem tela de autoatendimento na R1) |
| D5 | Metas da seção 3 | Mantidas |
| D6 | Dúvidas Q1–Q9 levantadas pelo agente na criação do backlog | Respondidas em RN-12 a RN-19 e na matriz de permissões (v1.1). A estória de login (E1.2) aguarda a ADR de autenticação |
| D7 | Dúvidas levantadas ao quebrar o login (E1.2) em estórias | Logout vira E1.6 (M); RN-20; RN-21; logo na tela de login aguarda a ADR de armazenamento (RN-17) |

## 12. Critérios de pronto da release

- Todas as features **M** entregues, com critérios de aceite testados automaticamente
- Dois tenants de demonstração com marcas diferentes funcionando em produção
- Uso completo pelo celular: o mecânico atualiza uma OS do início ao fim
- Documentação atualizada: PRD, ADRs, HLD e runbook básico

## 13. Rastreabilidade

| Épico | Jira | Documentos relacionados |
|---|---|---|
| E1 | SRV-5 | ADR multi-tenant, ADR autenticação |
| E2 | SRV-1 | — |
| E3 | SRV-2 | — |
| E4 | SRV-3 | — |
| E5 | SRV-4 | — |

## Histórico

| Versão | Data | Autor | Mudança |
|---|---|---|---|
| 0.1 | 2026-11-19 | Evandro + IA | Rascunho inicial a partir da visão do produto e do projeto MotoWorkshop |
| 1.0 | 2026-11-19 | Evandro | Aprovado. Perguntas em aberto resolvidas com as decisões D1–D5 |
| 1.1 | 2026-11-19 | Evandro | RN-12 a RN-19 e matriz de permissões, a partir das dúvidas levantadas na criação do backlog (D6). Chaves dos épicos E2–E5 |
| 1.2 | 2026-11-19 | Evandro | E1.6, RN-20 e RN-21 (D7) |
| 1.3 | 2026-11-19 | Evandro | RN-22 e RN-23, a partir das dúvidas na criação da SRV-21 |
