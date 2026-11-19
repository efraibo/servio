# ADR-0002: Autenticação e identificação do tenant: Spring Security com sessão JDBC e slug no caminho

- **Status:** Aceita
- **Data:** 2026-11-19
- **Decisor:** Evandro Nascimento
- **Relacionado:** RN-01, RN-18, RN-19, RNF-01, RNF-04 do PRD; matriz de permissões do PRD; features E1.2 e E1.4; estórias SRV-6 e SRV-7; ADR-0001 (condições 11, 12 e 13, ajustadas por esta ADR)

## Contexto

O login é a porta de entrada do isolamento multi-tenant: é dele que sai o tenant usado pelo RLS (ADR-0001). A ADR-0001 deixou em aberto como o login acessa dados antes de o tenant ser conhecido (condição 12) e de onde o tenant vem em requisições autenticadas (condição 11). A estória de login (E1.2) depende desta decisão.

Restrições reais:
- 1 desenvolvedor, cerca de 3h por semana de projeto. Escopo M em ~2 meses.
- Cloud gratuita (2 OCPU / 12 GB), com PostgreSQL, backend e frontend na mesma máquina.
- RN-19 (valores de referência): sessão expira em 8h; senha com no mínimo 8 caracteres; bloqueio de 15 minutos após 5 tentativas inválidas seguidas.
- Matriz de permissões do PRD: Gestor, Atendente e Mecânico, com a RN-08 (mecânico só altera as OS atribuídas a ele).
- RNF-04: OWASP Top 10 como checklist, senha com hash forte, sessão com expiração.
- Stack: Spring Boot, JPA/Hibernate 6, PostgreSQL, Angular (PWA, RNF-02). O front e a API ficam na mesma origem.
- Regras do decisor: só componentes padrão do Spring Security (nada de criptografia própria) e autenticação isolada atrás de uma interface, para poder trocar por Keycloak ou OIDC no futuro.

## Opções consideradas

### 1. Tecnologia de autenticação

#### Opção A — Spring Security, sem processo extra
- Prós: nenhum serviço a mais na máquina gratuita; controle total de RN-19 e da tela de login com marca; um só processo para operar.
- Contras: login, lockout e política de senha são código nosso; convite e recuperação de senha (E1.4 e E1.5) também.

#### Opção B — Spring Authorization Server
- Prós: OAuth2/OIDC padrão; boa base se houver clientes de terceiros.
- Contras: traz clientes, escopos e chaves sem benefício na R1; não resolve lockout nem convite; ainda exige tela de login e gestão de usuários próprias.

#### Opção C — Keycloak
- Prós: lockout, política de senha, convite e recuperação prontos.
- Contras: serviço JVM extra (consumo de memória na ordem de centenas de MB a cerca de 1 GB, estimativa não medida) em 2 OCPU / 12 GB; tema de login por tenant e mapeamento de tenant exigem configuração; mais uma peça para operar, atualizar e copiar em backup.

### 2. Mecanismo de sessão dentro da opção A

#### Opção A1 — JWT próprio em cookie HttpOnly
- Prós: expiração absoluta nativa (`exp`); nenhuma tabela de plataforma além de `tenants`; token assinado, que não fica guardado no banco.
- Contras: código nosso para emitir e ler o JWT; logout não invalida token roubado (vale até as 8h); revogação imediata (E1.4) e mudança de perfil exigem consulta por requisição, o que anula a vantagem de ser stateless.

#### Opção A2 — Sessão no servidor com Spring Session JDBC (PostgreSQL), cookie HttpOnly
- Prós: revogação real: as sessões de um usuário são removidas na hora (`FindByIndexNameSessionRepository`); logout invalida de fato; quase só configuração de componentes padrão; caminho natural para OIDC como BFF (`oauth2Login` mantém sessão, cookie e CSRF).
- Contras: duas tabelas de plataforma fora do RLS (`spring_session` e `spring_session_attributes`), lidas antes de o tenant ser conhecido; IDs de sessão ficam legíveis no banco (risco com SQL injection pelo papel da aplicação); a expiração do Spring Session é por inatividade, então a expiração absoluta de 8h exige um filtro pequeno; é preciso configurar serializador JSON no lugar da serialização Java.

| Critério | A1 | A2 |
|---|---|---|
| Revogação imediata (E1.4) | Consulta por requisição ou lista de bloqueio | Nativa |
| Mudança de perfil | Reler o perfil a cada requisição | Remover as sessões do usuário |
| Sessão de 8h absoluta | Nativa | Filtro de data de criação |
| XSS e CSRF | Cookie HttpOnly; CSRF necessário | Igual |
| Código de segurança próprio | Emissão e leitura do JWT | Filtro de expiração absoluta |
| Migração para OIDC | Troca do decoder; cookie exigiria BFF | BFF padrão do Spring |
| Exceções ao RLS | `tenants` | `tenants` e 2 tabelas de sessão |
| Se o banco vazar | Sem token guardado | IDs de sessão legíveis |

### 3. Como o tenant é identificado no login

#### Opção 3A — Slug no caminho (`/{slug}/login`)
- Prós: sem domínio nem certificado wildcard; login com marca; o mesmo e-mail pode existir em oficinas diferentes; trocar para subdomínio depois muda só o resolvedor do slug.
- Contras: cookie único entre oficinas na mesma origem (login em outra oficina substitui a sessão); manifest do PWA por caminho.

#### Opção 3B — Subdomínio (`oficina.servio.app`)
- Prós: tenant conhecido antes do login; cookie isolado por oficina; PWA por tenant.
- Contras: exige domínio próprio e certificado wildcard; DNS e ambiente local mais difíceis.

#### Opção 3C — E-mail único global
- Prós: login sem campo extra.
- Contras: a mesma pessoa não atua em duas oficinas; a existência de um e-mail vaza entre tenants; o tenant só é conhecido depois de consultar uma tabela global, o que força exceção ao RLS; login sem marca.

#### Opção 3D — Código da oficina digitado
- Prós: sem domínio.
- Contras: campo extra no celular; código enumerável; sem marca antes do código.

### 4. Como o login acessa as tabelas de plataforma sem burlar o RLS

#### Opção 4A — Credenciais sob RLS, tenant pelo slug
- Prós: só `tenants` (dados públicos) fica fora do RLS entre as tabelas de negócio; o login é acesso comum, sem papel nem função especial.
- Contras: só funciona se o tenant é conhecido antes do login (slug ou subdomínio).

#### Opção 4B — Função `SECURITY DEFINER`
- Prós: o banco restringe o que o login enxerga; serve também ao e-mail global.
- Contras: lógica em SQL, `search_path` fixo, chamada nativa no JPA, mais testes.

#### Opção 4C — Papel de banco `auth` com datasource própria
- Prós: simples no JPA.
- Contras: segundo pool; comprometer o papel expõe todos os hashes; é a mais próxima de burlar o RLS.

## Decisão

Adotar **Spring Security sem processo extra (A)**, com **sessão no servidor via Spring Session JDBC (A2)**, **tenant identificado pelo slug no caminho (3A)** e **credenciais sob RLS com o tenant resolvido pelo slug (4A)**. Motivo principal: revogação e logout reais (E1.4) com componentes padrão, sem serviço extra e sem exceção ao RLS para as credenciais.

1. **Componentes padrão.** Só componentes padrão do Spring Security. Senhas com `PasswordEncoder` (BCrypt ou Argon2). Nenhuma criptografia própria.
2. **Autenticação atrás de interface.** A verificação de credenciais fica atrás de uma interface (por exemplo, `Autenticador`) que devolve o principal (id do usuário, tenant e perfil). A implementação por credenciais locais é a única da R1. Código fora do módulo de autenticação depende só da interface. Trocar por Keycloak ou OIDC (como BFF com `oauth2Login`) muda a implementação, não a sessão, o cookie nem o CSRF.
3. **Slug no caminho.** O login é `/{slug}/login`. No login o slug é só dica de entrada, usada para localizar a credencial e validada pela senha. Depois do login vale apenas o tenant da sessão (condição 11 da ADR-0001).
4. **Slug divergente.** Se o slug da URL for diferente do tenant da sessão, a requisição é rejeitada (403 na API) e o front redireciona para o login da oficina do slug.
5. **Resolução em ponto único.** A resolução slug → tenant acontece no mesmo ponto único que define `app.tenant_id` (condição 3 da ADR-0001), não espalhada pelo código.
6. **Tabela `tenants` pública.** `tenants` guarda só dados públicos (nome, slug, cores, logo e ativo). Qualquer dado privado da oficina vai para tabela com `tenant_id` e RLS.
7. **Erro genérico.** Slug inexistente, oficina inativa, e-mail inexistente, senha errada e conta bloqueada retornam a mesma resposta genérica do login inválido, sem revelar o motivo. A verificação de senha também roda (com hash fictício) quando o usuário não existe, para não diferenciar pelo tempo de resposta.
8. **Credenciais sob RLS.** As credenciais ficam em tabela com `tenant_id` e RLS. O login define o tenant a partir do slug e as lê como tabela comum, sem exceção ao RLS.
9. **Sessão no servidor.** Spring Session JDBC no PostgreSQL. A sessão guarda o principal (id do usuário, tenant e perfil), serializado em JSON com os módulos do Spring Security, nunca em serialização Java.
10. **Tabelas de sessão.** `spring_session` e `spring_session_attributes` entram na lista de plataforma (condição 12 da ADR-0001) e são acessadas só pelo Spring Session, nunca por código da aplicação.
11. **Cookie.** Cookie de sessão com `Secure`, `HttpOnly` e `SameSite=Lax`. CSRF com o suporte padrão do Angular (cookie `XSRF-TOKEN` legível; o cookie de sessão continua HttpOnly).
12. **Session fixation.** Troca do ID de sessão no login, proteção padrão do Spring Security que deve ficar ativa.
13. **Expiração absoluta de 8h (RN-19).** A sessão expira 8h após a criação, sem timeout por inatividade, que não está no PRD. Um filtro confere a data de criação e invalida a sessão. O `maxInactiveInterval` do Spring Session, obrigatório, fica em 8h só como limpeza de sessões abandonadas e não é uma regra de negócio.
14. **Revogação imediata.** Ao desativar um usuário ou mudar o perfil dele, todas as sessões desse usuário são removidas na hora, via `FindByIndexNameSessionRepository`. O nome do principal é o id do usuário, único entre tenants. É o motivo da escolha da A2.
15. **Política de senha e bloqueio (RN-19).** Senha com no mínimo 8 caracteres. Após 5 tentativas inválidas seguidas, a credencial é bloqueada por 15 minutos. O contador é por credencial, zera no sucesso e é gravado em transação própria (`REQUIRES_NEW`), para sobreviver à falha do login. O `SET LOCAL app.tenant_id` só vale na transação em que foi executado, então essa transação própria começa sem `app.tenant_id`; com o RLS em falha fechada, o `UPDATE` do contador afetaria zero linhas, sem erro. Por isso o ponto único que define `app.tenant_id` (condição 3 da ADR-0001) atua no início de **toda** transação, inclusive `REQUIRES_NEW`, e não só no início da requisição. O contador é gravado e lido sob RLS, e a gravação que afeta zero linhas é tratada como erro, nunca como sucesso silencioso.
16. **Autorização por perfil.** O perfil (Gestor, Atendente, Mecânico) fica no principal e vira autoridade do Spring Security. A matriz de permissões é aplicada por método (`@PreAuthorize`). A RN-08 (mecânico só altera as OS atribuídas) é aplicada no serviço.
17. **SQL injection.** Acesso a dados só com JPA ou consultas parametrizadas, verificado por revisão e por análise estática no CI. A ferramenta é definida na estória de CI.

## Consequências

- **Positivas:**
  - Revogação e logout reais, sem código de segurança próprio além de um filtro pequeno.
  - Sem serviço extra na máquina gratuita.
  - As credenciais ficam sob RLS: a única exceção entre tabelas de negócio é `tenants`, com dados públicos; as outras duas são tabelas do framework.
  - Caminho de migração definido para OIDC ou Keycloak, por trás da interface.
- **Negativas / dívidas aceitas:**
  - IDs de sessão ficam legíveis no banco. Um SQL injection com o papel da aplicação poderia ler sessões de qualquer tenant. É mitigado pela regra 17 e por revisão; não é eliminado.
  - O filtro de expiração absoluta é código próprio, pequeno, e precisa de teste.
  - Cookie único entre oficinas na mesma origem: fazer login em outra oficina no mesmo navegador substitui a sessão. A regra 4 trata a sessão antiga com redirecionamento.
  - Bloqueio por credencial permite que alguém bloqueie o Gestor de propósito (15 minutos). Aceito na R1.
  - O PWA por caminho (manifest por slug) fica mais restrito que por subdomínio. Se houver domínio próprio no futuro, trocar o resolvedor do slug para subdomínio.
  - Convite e recuperação de senha (E1.4 e E1.5, prioridade S) continuam por construir.
- **O que precisa ser feito por causa desta decisão** (estórias técnicas propostas; não criadas no Jira):
  1. Definir a interface de autenticação e a implementação por credenciais locais.
  2. Configurar `PasswordEncoder` (BCrypt ou Argon2) e a política de senha de 8 caracteres.
  3. Implementar o lockout (5 tentativas, 15 minutos) com contador em transação própria.
  4. Implementar a resolução slug → tenant no ponto único que define `app.tenant_id` (ADR-0001, condição 3).
  5. Configurar Spring Session JDBC com serializador JSON, cookie `Secure`/`HttpOnly`/`SameSite=Lax` e troca do ID no login.
  6. Implementar o filtro de expiração absoluta de 8h.
  7. Remover as sessões do usuário ao desativá-lo ou mudar o perfil (depende de E1.4).
  8. Configurar CSRF com o Angular.
  9. Tela de login com a marca do tenant por slug e redirecionamento quando o slug diverge do tenant da sessão.
  10. Autorização por perfil com `@PreAuthorize` (SRV-7).
  11. Ajustar o script de provisionamento (SRV-6) para criar o primeiro Gestor com credencial (RN-18).
  12. Adicionar análise estática de SQL injection ao CI.
  13. Atualizar a ADR-0001 (feito junto desta ADR), o HLD e o runbook (papéis de banco, tabelas de plataforma, sessão).

## Como verificar

Testes automáticos em PostgreSQL real (Testcontainers), salvo indicação:
- **Login e principal:** login válido cria sessão com tenant e perfil corretos.
- **Session fixation:** o ID de sessão depois do login é diferente do anterior ao login.
- **Revogação:** desativar um usuário e mudar o perfil removem todas as sessões dele na hora; a requisição seguinte com o cookie antigo dá 401.
- **Expiração absoluta:** com relógio controlado, a sessão expira 8h após a criação mesmo com atividade contínua, e não expira antes por inatividade.
- **Cookie:** o cookie de sessão tem `Secure`, `HttpOnly` e `SameSite=Lax`.
- **CSRF:** requisição de escrita sem token CSRF dá 403.
- **Slug divergente:** com sessão do tenant A, requisição com slug do tenant B dá 403 na API; o front redireciona para o login do slug B (teste de front).
- **Erro genérico:** slug inexistente, oficina inativa, e-mail inexistente, senha errada e conta bloqueada retornam o mesmo status e o mesmo corpo.
- **Lockout:** 5 tentativas inválidas seguidas bloqueiam por 15 minutos; sucesso antes do limite zera o contador; o contador persiste mesmo com a falha do login.
- **Contador sob RLS e bloqueio na 5ª tentativa:** com o papel restrito da aplicação (sem `BYPASSRLS`, `FORCE RLS` ativo) e dois tenants com a mesma credencial de e-mail, 5 logins inválidos seguidos no tenant A: (a) após cada tentativa, o contador lido sob o tenant A vale 1, 2, 3, 4 e 5, ou seja, a gravação em `REQUIRES_NEW` afetou exatamente 1 linha (prova de que o `app.tenant_id` foi definido nessa transação); (b) as tentativas 1 a 4 retornam o erro genérico e a 5ª também, mas a 6ª, mesmo com a senha correta, é recusada pelo bloqueio, e a 5ª já deixa a credencial bloqueada; (c) o contador do tenant B permanece 0 e não é lido nem alterado; (d) lido sem `app.tenant_id`, o contador não retorna linha.
- **Tenant em toda transação:** teste de integração abre uma transação `REQUIRES_NEW` dentro de outra e confirma que `current_setting('app.tenant_id')` está definido com o tenant correto em ambas; uma gravação que afeta zero linhas por falta de `app.tenant_id` faz o teste falhar com erro explícito.
- **Senha:** senha com menos de 8 caracteres é rejeitada; o banco guarda só o hash (BCrypt ou Argon2), nunca texto.
- **Credenciais sob RLS:** com o tenant A definido, as credenciais do tenant B não são lidas.
- **`tenants` pública:** teste de esquema confirma que `tenants` tem só as colunas da lista nominal (nome, slug, cores, logo, ativo).
- **Lista nominal de plataforma:** o teste de cobertura de RLS da ADR-0001 passa com exatamente `tenants`, `spring_session` e `spring_session_attributes` como exceções.
- **Arquitetura:** teste de arquitetura (ex.: ArchUnit) falha se código da aplicação acessar `spring_session*` fora do Spring Session, e se código fora do módulo de autenticação depender de classes concretas dele em vez da interface.
- **Autorização:** testes por perfil conforme a matriz do PRD, incluindo a RN-08.
- **SQL injection:** revisão e análise estática no CI; nenhuma consulta com concatenação de entrada.
