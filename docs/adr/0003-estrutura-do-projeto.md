# ADR-0003: Estrutura do projeto: monorepo, stack, módulos, ambiente local e verificação única

- **Status:** Aceita
- **Data:** 2026-11-20
- **Decisor:** Evandro Nascimento
- **Relacionado:** PRD v1.3 (RNF-01, RNF-02, RNF-03, RNF-08, RNF-09, RNF-10; épicos E1 a E5); `docs/processo/definicao-de-pronto.md`; ADR-0001 (multi-tenant com RLS) e ADR-0002 (autenticação e tenant)

## Contexto

O PRD e as ADR-0001 e ADR-0002 estão aprovados, mas ainda não existe código. Antes da primeira estória de código é preciso definir como o repositório é organizado, quais versões usar, como o backend se divide em módulos, como o front é montado, como o ambiente local e os testes sobem o banco, e qual comando verifica tudo. O DoR proíbe estória que dependa de decisão em aberto, então esta ADR destrava o backlog técnico.

Restrições reais:
- 1 desenvolvedor, cerca de 3h por semana. O escopo M precisa caber em ~2 meses, ou seja, cerca de 24h no total. Cada peça de fundação consome estórias do backlog funcional.
- Cloud gratuita (Oracle Always Free, 2 OCPU / 12 GB), com PostgreSQL, backend e frontend na mesma máquina. Essa máquina costuma ser arm64, e o Mac de desenvolvimento também: imagens e JDK precisam ser multi-arquitetura.
- ADR-0001: RLS exige papéis de banco separados, PostgreSQL real nos testes (Testcontainers) e teste de cobertura de RLS.
- ADR-0002: front e API na mesma origem; tela de login por slug; autenticação atrás de interface.
- Hook do Claude Code, pessoas e CI precisam rodar a mesma verificação (RNF-08).

## Opções consideradas

### 1. Organização do repositório

#### Opção A — Monorepo simples (`backend/`, `frontend/`, `docs/`, `infra/`, script na raiz)
- Prós: um PR cobre back e front; uma CI; docs versionados junto do código; sem ferramenta extra.
- Contras: sem cache incremental entre back e front; dois gerenciadores de dependência (Maven e npm).

#### Opção B — Dois repositórios (back e front)
- Prós: ciclos de release independentes.
- Contras: estória que toca API e tela vira dois PRs; ID SRV-xx duplicado; CI duplicada. Custo alto para 1 pessoa.

#### Opção C — Monorepo com orquestrador (Nx, Turborepo)
- Prós: cache e grafo de tarefas.
- Contras: ferramenta a mais para aprender e manter; ganho irrelevante com 2 projetos.

### 2. Versões

Verificadas em fonte oficial em 2026-11-20.

| Componente | Opção escolhida | Alternativa considerada | Fonte e fato verificado |
|---|---|---|---|
| Java | **25 (LTS)** | 21 (LTS) | [OpenJDK](https://openjdk.org/projects/jdk/25/): GA em 2025-09-16. [Adoptium](https://adoptium.net/support/): Temurin 25.0.5 (out/2026), suporte "ao menos set/2031"; 21 "ao menos dez/2029". A página de roadmap da Oracle devolveu 403, então a data de Premier Support não foi lida na fonte |
| Spring Boot | **4.1.3** | 4.2.0 (publicada em 2026-11-19) | [Anúncio da 4.2.0](https://spring.io/blog/2026/11/19/spring-boot-4-2-0-available-now/); a 4.1.3 consta como estável no seletor de versões da [documentação](https://docs.spring.io/spring-boot/system-requirements.html) |
| Spring Modulith | **2.1.x** | 2.2.0 (exige Boot 4.2.0 e Framework 7.1) | [Release 2.2 GA](https://github.com/spring-projects/spring-modulith/releases/tag/2.2.0); [blog](https://spring.io/blog/2026/10/23/spring-modulith-2-2-rc1-2-1-2-2-0-9-and-1-4-14-released/): 2.1.2 anunciada em 2026-10-23 |
| Angular | **22** | 21 (LTS até 2027-06) | [angular.dev](https://angular.dev/reference/releases): 22 lançada em 2026-06-03, ativa até 2027-06, LTS até 2028-06. A 20 termina em 2026-11-28 |
| Node.js | **26** | 24 (manutenção desde 2026-10-20) | [schedule.json](https://raw.githubusercontent.com/nodejs/Release/main/schedule.json): 26 em LTS ativa desde 2026-10-28, fim em 2029-04-30. [Angular](https://angular.dev/reference/versions): a 22 aceita `^22.22.3 \|\| ^24.15.0 \|\| ^26.0.0` |
| PostgreSQL | **18** (18.7) | 19 (GA em 2026-11-19) | [Versioning policy](https://www.postgresql.org/support/versioning/): 18 recebe correções até 2030-11-14; 19 até 2031-11-19 |
| Testcontainers | **2.0.5** | — | [java.testcontainers.org](https://java.testcontainers.org/) |
| Build do backend | **Maven com wrapper** | Gradle 9 | Boot suporta os dois; Maven é declarativo, com menos superfície para o agente errar |

Opção de Spring Boot e Modulith:
- **Boot 4.2.0 + Modulith 2.2.0:** prós: linha mais nova, mais tempo de suporte; contras: a 4.2.0 saiu um dia antes desta ADR, sem correções de patch.
- **Boot 4.1.3 + Modulith 2.1.x:** prós: linha com patches já publicados; contras: exige subir de minor em breve.
- **Boot 3.5.x:** descartada, pois é a geração anterior (Framework 6).

Opção de PostgreSQL: a 19 saiu um dia antes desta ADR; a 18 tem um ano de maturidade, mais de 4 anos de suporte e o RLS é um recurso antigo.

### 3. Módulos do backend (Spring Modulith)

#### Opção A — Um módulo por épico, com E1 dividido
- Prós: casa com o PRD e com o Jira; poucos módulos, fácil para 1 pessoa.
- Contras: o módulo `cadastros` agrupa três conceitos (clientes, motos, catálogo).

#### Opção B — Um módulo por agregado
- Prós: fronteiras finas.
- Contras: mais módulos e mais eventos; cerimônia demais para ~24h.

#### Opção C — Módulo único em camadas
- Prós: zero cerimônia.
- Contras: perde a fronteira verificável; Modulith e ArchUnit não protegem nada.

Sobre eventos entre módulos: eventos em memória do Modulith (prós: sem tabela extra; contras: evento não sobrevive a queda no meio do processamento, aceitável na R1) ou publicação de eventos em tabela JDBC (prós: durabilidade; contras: mais uma tabela que precisa de RLS ou entra na lista nominal da ADR-0001).

Sobre migrações: Flyway com SQL puro (prós: o template de RLS por tabela fica em SQL, sem camada de abstração; contras: sem rollback automático) ou Liquibase (prós: formatos e rollback; contras: verboso para 1 pessoa).

### 4. Workspace Angular

#### Opção A — Angular CLI com múltiplos projetos (`gestao`, `portal`, lib `shared`)
- Prós: padrão do framework; sem ferramenta extra; um `npm ci`.
- Contras: sem cache de tarefas (irrelevante neste porte).

#### Opção B — Nx
- Prós: cache e grafo de tarefas.
- Contras: curva de aprendizado e manutenção extra.

#### Opção C — Dois projetos Angular separados
- Prós: isolamento total.
- Contras: duplica configuração e tema; a lib compartilhada vira pacote publicado.

Biblioteca de UI: **Angular Material** (prós: acessibilidade pronta, RNF-10; tema por variáveis CSS; contras: visual padronizado, e a troca de cores em runtime precisa ser confirmada) ou Tailwind com CDK (prós: controle visual; contras: mais trabalho de acessibilidade).

Manifest do PWA: estático e genérico (prós: instalável sem custo; contras: não carrega a marca por slug) ou dinâmico por slug (prós: marca por oficina; contras: endpoint e estória a mais).

### 5. Ambiente local e testes

#### Opção A — `infra/compose.yaml` só com PostgreSQL; backend e front rodam no host
- Prós: ciclo rápido (hot reload, debugger); pouca memória.
- Contras: o stack completo não é exercitado localmente.

#### Opção B — Compose com tudo (Postgres, backend, front e proxy)
- Prós: parecido com produção.
- Contras: rebuild de imagem a cada mudança; lento com 3h por semana.

#### Opção C — Spring Boot Docker Compose support
- Prós: `spring-boot:run` sobe o compose sozinho.
- Contras: acoplamento implícito à versão da Boot.

### 6. Comando único de verificação

#### Opção A — `./verify` (script bash na raiz)
- Prós: sem dependência além de Java, Node e Docker; igual para pessoa, hook e CI; legível.
- Contras: só Unix (macOS e Linux cobrem dev e CI).

#### Opção B — `make verify`
- Prós: convencional.
- Contras: `make` a mais; sintaxe ruim para lógica.

#### Opção C — Só `./mvnw verify` com plugin que chama o npm
- Prós: um comando.
- Contras: Maven vira orquestrador do front; erro de lint do front aparece como log de Maven.

#### Opção D — `just` ou Taskfile
- Prós: ergonomia.
- Contras: ferramenta a instalar em máquina, hook e CI.

### 7. Convenções de branch, commit e PR

#### Opção A — Conventional Commits com ID, trunk-based, squash merge
- Prós: já é o estilo do histórico (`docs: ...`); ID rastreável no Jira; tipo do commit visível.
- Contras: exige validação automática para não esquecer o ID.

#### Opção B — Prefixo do Jira sem tipo (`SRV-12: ...`)
- Prós: mais simples; integra com smart commits.
- Contras: perde o tipo (feat, fix, docs).

#### Opção C — Git Flow
- Prós: familiar.
- Contras: cerimônia inútil para 1 desenvolvedor.

## Decisão

Adotar as opções abaixo. O motivo geral é o menor custo de fundação para 1 pessoa em ~3h por semana, sem abrir mão das garantias das ADR-0001 e ADR-0002.

### 1. Repositório: Opção A

Monorepo simples com `backend/`, `frontend/`, `docs/`, `infra/`, `verify` na raiz, `.claude/` (hooks) e `CLAUDE.md`.

```
/
├── backend/     (Maven; módulos Modulith)
├── frontend/    (Angular CLI: projects/gestao, projects/portal, projects/shared)
├── infra/       (compose.yaml, postgres/init/)
├── docs/        (produto, processo, adr)
├── verify       (comando único)
├── .claude/     (hooks)
└── CLAUDE.md
```

### 2. Versões

Java **25**, Spring Boot **4.1.3**, Spring Modulith **2.1.x**, Angular **22**, Node **26**, PostgreSQL **18**, Testcontainers **2.0.5**, Maven com wrapper.

- A Boot 4.2.0 não é usada porque saiu um dia antes desta ADR. Subir para a 4.2.x (e o Modulith para a 2.2.x, que exige Boot 4.2.0 e Framework 7.1) é **estória própria**, a abrir quando sair a 4.2.1.
- A matriz oficial de compatibilidade do Modulith não lista a 2.1; o par com a Boot 4.1 deve ser confirmado no scaffold.
- **Primeira tarefa da fundação: spike do Hibernate 7.** A ADR-0001 cita Hibernate 6 (`@TenantId`, `CurrentTenantIdentifierResolver`) e a Boot 4 deve trazer o Hibernate 7. Se confirmar, a referência da ADR-0001 é ajustada (não é mudança de decisão).
- Lacunas de verificação, registradas como lacunas: a página de roadmap da Oracle devolveu 403; as datas de fim do suporte aberto da Boot 4.x não constam nas páginas oficiais lidas (política publicada: minor com ao menos 12 meses); o Java mínimo e as dependências (Hibernate, Security, Session) da linha 4.1 não foram lidos na documentação dela.

### 3. Módulos do backend: Opção A

**Pacote raiz do backend: `app.servio`**, com um subpacote por módulo (`app.servio.tenant`, `app.servio.acesso`, `app.servio.cadastros`, `app.servio.agenda`, `app.servio.os`, `app.servio.painel`). Eventos em memória. Flyway com SQL puro (`db/migration`). Identificadores: **domínio em português, infraestrutura em inglês** (por exemplo, `Autenticador` no módulo `acesso` e `TenantContext` na infraestrutura).

| Módulo | Épico | Conteúdo | Depende de |
|---|---|---|---|
| `tenant` | E1 (plataforma) | tabela `tenants`, ponto único do `app.tenant_id` (ADR-0001, condição 3), provisionamento (RN-18, RN-23), marca (E1.1) | — |
| `acesso` | E1 (acesso) | interface `Autenticador`, usuários, perfis, sessão, lockout, logout (ADR-0002) | `tenant` |
| `cadastros` | E2 | clientes, motos, catálogo de serviços | `tenant` |
| `agenda` | E3 | agendamento, capacidade, horário de funcionamento | `cadastros` |
| `os` | E4 | ordem de serviço, itens, status, atribuição, RN-08 no serviço | `cadastros`, `agenda` (só API pública) |
| `painel` | E5 | leitura agregada de agenda e OS | `agenda`, `os` |

- **Ciclo agenda e os (RN-11):** o agendamento vira OS. `os` publica o evento `OsAberta` e `agenda` o escuta para marcar "Em atendimento". `os` lê o agendamento só pela API pública de `agenda`.
- Sem tabela de publicação de eventos na R1.
- As regras de fronteira são verificadas por `ApplicationModules.verify()` e ArchUnit, junto das regras já exigidas pela ADR-0001 (condição 13) e pela ADR-0002 (acesso a `spring_session*` e dependência só da interface `Autenticador`).

### 4. Workspace Angular: Opção A

- `frontend/projects/gestao`: toda a R1. `frontend/projects/portal`: **esqueleto vazio** (R2). `frontend/projects/shared`: biblioteca com tokens de design e tema white-label (RNF-03, variáveis CSS aplicadas em runtime), cliente HTTP, interceptor de CSRF (ADR-0002, regra 11) e tratamento de 401 e de 403 por slug divergente (regra 4).
- **Angular Material**, sujeito ao spike de tema em runtime (trocar cores do tenant só com variáveis CSS, sem build por cliente). Se o spike falhar, reavaliar a opção Tailwind com CDK em nova ADR.
- **PWA** (`@angular/pwa`) só na `gestao`, com **manifest estático** e genérico (`start_url: /`). O manifest dinâmico por slug, aceito como limitação na ADR-0002, fica para estória posterior.
- Mobile-first: estilos base para 360px, crescendo por breakpoints. Lint com `angular-eslint`; runner de testes padrão gerado pelo `ng new` da versão 22 (a confirmar no scaffold).
- Mesma origem (ADR-0002): em dev, o `ng serve` faz proxy de `/api` para o backend; em produção, um proxy reverso serve estático e API na mesma origem. O desenho do deploy fica para outra ADR.

### 5. Ambiente local: Opção A

`infra/compose.yaml` só com PostgreSQL 18; backend e front rodam no host. **O mesmo script de papéis** (`infra/postgres/init/`, que cria o dono das migrações e o papel restrito da aplicação, ADR-0001 condição 1) alimenta o compose e o Testcontainers, para o teste de RLS rodar com os mesmos papéis do desenvolvimento.

- Testcontainers com PostgreSQL 18 real, container compartilhado por execução (singleton), usando `@ServiceConnection` onde servir. Os nomes dos módulos do Testcontainers 2.x devem ser conferidos no scaffold.
- Imagem `postgres:18`: o volume monta em `/var/lib/postgresql` e o `PGDATA` passa a ser por versão ([Docker Hub](https://hub.docker.com/_/postgres)); o compose deve respeitar isso.
- Dados fictícios em dev e testes (RNF-05).
- Compose completo (opção B) fica para a ADR de deploy.

### 6. Comando único: `./verify`

Script bash na raiz, usado por pessoas, pelo hook do Claude Code e pela CI. Ordem de falha rápida:

1. **Front:** lint (`angular-eslint`), testes e build de `gestao` e `portal`.
2. **Back:** `./mvnw verify`, com testes unitários, `ApplicationModules.verify()`, ArchUnit e testes de integração com Testcontainers (teste de cobertura de RLS da ADR-0001 condição 4, isolamento por recurso e falha fechada).
3. **Segurança estática:** análise de SQL injection (ADR-0002, regra 17) e varredura de segredos (DoD). As ferramentas são escolhidas na estória de CI.

"Cobertura de RLS" significa o teste de cobertura de RLS da ADR-0001. Cobertura de código (JaCoCo) é apenas medida, sem percentual mínimo na R1.

| Modo | Uso | O que roda |
|---|---|---|
| `./verify` | antes do PR e **CI** (a CI instala JDK, Node e cache e chama este comando) | tudo |
| `./verify --hook` | **hook `Stop` do Claude Code** | detecta pelo `git diff` quais lados mudaram e roda **só eles, cada um completo** (ver regras abaixo) |
| `./verify --back` e `--front` | trabalho focado | só um dos lados, completo |

Regras do modo `--hook`:
- A detecção usa o `git diff` das alterações não commitadas **e** da branch em relação à `main`. Mudou arquivo em `backend/`: roda o backend completo, **incluindo os testes com Testcontainers**. Mudou arquivo em `frontend/`: roda o front completo (lint, testes e build de `gestao` e `portal`).
- Se mudarem os dois lados, ou arquivos de `infra/` ou do próprio `verify`, roda os dois.
- Se só `docs/` ou outros arquivos fora desses diretórios mudarem, não roda nenhum lado.
- Nunca roda um lado de forma parcial: o backend que roda sempre inclui os testes com Testcontainers, porque são eles que provam o isolamento e a cobertura de RLS (ADR-0001).
- **Meta do modo do hook: até 3 minutos.** Acima disso, reavaliar em **nova ADR**.
- Quando falha, o hook imprime só as últimas linhas relevantes e sai com código 2, para o agente ler o erro sem encher o contexto.

### 7. Convenções: Opção A

- Trunk-based. Branch curta por estória: `SRV-12-slug-curto`. Commit: `feat(SRV-12): resumo em português`. PR: `SRV-12: título`. **Squash merge** em `main`, protegida com CI verde obrigatória (RNF-08).
- Tipos: `feat`, `fix`, `test`, `refactor` (**ID `SRV-xx` obrigatório**); `docs`, `chore`, `ci` e `build` (**isentos**). O PR sempre leva o ID. A DoD foi ajustada com essa regra.
- Validação: hook `commit-msg` do git (local) e checagem na CI do título do PR e dos commits. A CI é a barreira que vale.

## Consequências

- **Positivas:**
  - Um repositório, uma CI e um comando de verificação iguais para pessoas, hook e CI.
  - Fronteiras de módulo verificáveis desde o primeiro dia, alinhadas aos épicos e ao Jira.
  - O teste de RLS roda com os mesmos papéis do ambiente local, sem divergência.
  - Versões em linhas com suporte longo (Java 25, Angular 22, Node 26, PostgreSQL 18).
- **Negativas / dívidas aceitas:**
  - Boot 4.1 envelhece mais cedo: subir para a 4.2.x é trabalho futuro, com o Modulith junto.
  - O spike do Hibernate 7 e o spike do tema em runtime podem exigir ajuste de ADR-0001 ou da escolha do Angular Material.
  - Eventos em memória não sobrevivem a uma queda no meio do processamento; aceitável na R1.
  - O modo do hook pode ficar lento com Testcontainers; a meta de 3 minutos é medida, e acima disso há nova ADR.
  - Manifest estático do PWA: a instalação não carrega a marca por oficina.
  - Fundação custa cerca de 7 estórias de até 3h (~3 das ~8 semanas de capacidade do escopo M).
  - Lacunas de verificação: Premier Support do Java 25 pela Oracle, fim do suporte aberto da Boot 4.x e dependências da linha 4.1 (ver Decisão, item 2).
- **O que precisa ser feito por causa desta decisão** (estórias técnicas propostas; não criadas no Jira). Ordem sugerida, como **fatia vertical mínima** (login até o banco, passando por `./verify` e CI), adiando PWA e manifest dinâmico:
  1. Spike do Hibernate 7 (`@TenantId`, `CurrentTenantIdentifierResolver`) e do tema em runtime com Angular Material; ajustar ADR-0001 se necessário.
  2. Scaffold do backend: Maven, Boot 4.1.3, Modulith 2.1.x, Flyway, `compose.yaml`, script de papéis e infraestrutura de Testcontainers.
  3. `./verify` (modos completo, `--hook`, `--back`, `--front`) e CI com `main` protegida.
  4. Scaffold do frontend: workspace com `gestao`, `portal` vazio e `shared`, lint e testes.
  5. Hook `Stop` do Claude Code com `./verify --hook`, hook `commit-msg` e checagem de título de PR e commits na CI.
  6. Ferramentas de análise estática de SQL injection e de varredura de segredos.
  7. `CLAUDE.md` com a estrutura, o comando `./verify` e as convenções.
  8. Futuro: subir para Boot 4.2.x e Modulith 2.2.x quando sair a 4.2.1; manifest do PWA por slug; compose completo na ADR de deploy.

## Como verificar

- **Fronteiras de módulo (automático, CI):** `ApplicationModules.verify()` e os testes ArchUnit rodam em `./verify` e falham se um módulo depender de outro fora da tabela de dependências ou de classe interna.
- **Comando único:** `./verify` na CI é o mesmo que roda localmente. A CI falha se `./verify` falhar; o workflow não repete as etapas por conta própria.
- **Hook (seleção por diff):** teste automático do script, em repositório git temporário, confirma que `./verify --hook` roda: só o backend quando só `backend/` mudou (alteração não commitada ou da branch em relação à `main`); só o front quando só `frontend/` mudou; os dois quando ambos mudaram ou quando mudou arquivo de `infra/` ou do próprio `verify`; nenhum quando só `docs/` mudou. Confirma também que o backend, sempre que roda, inclui os testes com Testcontainers (inclui o teste de cobertura de RLS).
- **Hook (tempo):** a duração é registrada a cada execução; acima de 3 minutos abre-se nova ADR.
- **Pacote raiz:** ArchUnit falha se existir classe de produção fora de `app.servio.<módulo>` ou se o módulo não for um dos seis da tabela.
- **Papéis idênticos:** teste confirma que o papel da aplicação no Testcontainers e no compose vem do mesmo script em `infra/postgres/init/` (não há segunda definição).
- **Versões:** `./verify` falha se o Java ou o Node em uso estiverem fora das versões desta ADR (checagem no início do script).
- **Convenções:** a CI rejeita PR sem `SRV-xx` no título e commits `feat`, `fix`, `test` ou `refactor` sem `SRV-xx`; o hook `commit-msg` faz a mesma checagem localmente. `main` rejeita merge sem CI verde.
- **Revisão:** o agente revisor e a revisão humana conferem, em estórias de fundação, o checklist desta ADR.
