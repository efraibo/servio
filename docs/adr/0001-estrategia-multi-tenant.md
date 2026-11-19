# ADR-0001: Estratégia multi-tenant: banco compartilhado com `tenant_id` e Row Level Security

- **Status:** Aceita
- **Data:** 2026-11-19
- **Decisor:** Evandro Nascimento
- **Relacionado:** RN-01, RN-03, RN-18, RNF-01, RNF-05, RNF-09 do PRD; estórias SRV-6 e SRV-7; ADR-0002 (autenticação, a ser escrita)

## Contexto

O Servio é uma plataforma white-label: cada oficina é um tenant e nenhum usuário pode ver dados de outro (RN-01). O RNF-01 exige que o isolamento seja garantido no banco, não só na aplicação, e que a estratégia seja definida em ADR. Esta decisão precisa sair antes das primeiras estórias que acessam dados (E1 e E2), porque muda o modelo de tabelas, as migrações e a camada de persistência.

Restrições reais:
- 1 desenvolvedor, cerca de 3h por semana de projeto. O escopo M precisa caber em ~2 meses.
- Cloud gratuita (Oracle Always Free, 2 OCPU / 12 GB). PostgreSQL, backend e frontend dividem a mesma máquina.
- Volume da R1: até 5.000 OS por tenant (RNF-07) e 2 tenants de demonstração. Não há centenas de tenants.
- Primeiro tenant e primeiro Gestor criados por script de provisionamento, sem tela de administração (RN-18).
- Logs estruturados com `tenantId` (RNF-09).
- Stack: Java, Spring Boot, JPA/Hibernate 6, PostgreSQL.

## Opções consideradas

### Opção A — Banco compartilhado com `tenant_id` + Row Level Security (RLS) do PostgreSQL
- Prós:
  - Um único schema e uma única migração, o que melhor cabe em 3h por semana.
  - O isolamento fica no banco: a política de RLS filtra mesmo que o código esqueça o `WHERE tenant_id`. Atende o RNF-01 literalmente.
  - Baixo consumo de memória e de conexões, com um pool só para todos os tenants.
  - Provisionar tenant é inserir uma linha.
  - Um mesmo teste de isolamento cobre todas as tabelas.
- Contras:
  - O RLS só vale se a aplicação não conectar como dono da tabela nem com `BYPASSRLS`. Exige papéis de banco separados para aplicação e migração.
  - A aplicação precisa informar o tenant a cada transação (`SET LOCAL`). Com pool de conexões, um valor que vaze entre requisições quebra o isolamento.
  - Toda tabela de negócio precisa de `tenant_id` e de política. Tabela nova sem política fica aberta.
  - Unicidade por tenant precisa de índices compostos com `tenant_id` (RN-03).
  - Um tenant pode afetar o desempenho dos outros; aceitável no volume da R1.
  - Backup e exclusão por tenant dependem de script.

### Opção B — Schema por tenant (mesmo banco)
- Prós:
  - Isolamento natural por schema, sem `tenant_id` nas tabelas.
  - Backup, restauração e remoção por tenant são fáceis.
  - O Hibernate oferece suporte a schema por tenant.
- Contras:
  - Toda migração roda N vezes, com risco de falha parcial; é trabalho extra para 1 pessoa.
  - O isolamento depende de `search_path` e de permissões; um erro de configuração expõe outro schema, sem barreira própria do banco como no RLS.
  - Cache de metadados e conexões do Hibernate crescem por tenant.
  - Consultas globais exigem `UNION` entre schemas.
  - Provisionar tenant exige criar o schema e aplicar todas as migrações, o que complica o script da RN-18.

### Opção C — Banco por tenant
- Prós:
  - Isolamento máximo e fácil de explicar; backup, restauração e exclusão por banco.
  - Permite mover um tenant para outra máquina no futuro.
- Contras:
  - Cada banco consome memória e processos, o que pesa em 2 OCPU / 12 GB.
  - Exige uma datasource por tenant e roteamento dinâmico, a maior carga de infraestrutura para 1 desenvolvedor.
  - Migração em N bancos, com o problema da opção B ampliado.
  - Provisionar tenant exige criar banco e usuário.
  - Isolamento acima do necessário para o porte da R1.

### Comparação

| Critério | A (RLS) | B (schema) | C (banco) |
|---|---|---|---|
| Isolamento garantido no banco (RNF-01) | Sim, com papel restrito e `FORCE RLS` | Parcial (depende de `search_path` e permissões) | Sim |
| Esforço para 1 dev em 3h/semana | Baixo | Médio | Alto |
| Memória em 12 GB | Baixo | Baixo a médio | Alto |
| Migrações | 1 vez | N vezes | N vezes |
| Provisionar tenant (RN-18) | Insert | Criar schema e migrar | Criar banco e migrar |
| Backup e exclusão por tenant | Por script | Fácil | Fácil |

## Decisão

Adotar a **Opção A**: banco PostgreSQL compartilhado, com `tenant_id` em toda tabela de negócio e isolamento garantido por Row Level Security, somado a um filtro de tenant na aplicação (defesa em camadas). Motivo principal: cumpre o RNF-01 com a garantia no banco, com o menor custo operacional para uma pessoa só e pouca memória.

A decisão vale com as 13 condições abaixo.

1. **Papéis de banco separados.** A aplicação conecta com um papel sem `BYPASSRLS`, que não é dono das tabelas e não é superuser. As migrações rodam com outro papel, dono do schema.
2. **RLS em toda tabela de negócio.** `ENABLE ROW LEVEL SECURITY` e `FORCE ROW LEVEL SECURITY`, com política baseada em `current_setting('app.tenant_id')`.
3. **Contexto por transação.** O `tenant_id` é definido com `SET LOCAL` (ou `set_config(..., true)`), em um único ponto da aplicação, para não vazar entre requisições no pool de conexões.
4. **Teste de cobertura de RLS.** Um teste automático falha se existir tabela com `tenant_id` sem RLS ativo e forçado, ou sem política.
5. **Teste de isolamento por recurso.** Dados os tenants A e B, A não lê nem escreve dados de B (exigido pela DoD sempre que a estória acessa dados).
6. **Unicidade composta com `tenant_id`.** Restrições e índices únicos incluem o `tenant_id` (ex.: placa única por tenant, RN-03).
7. **Log com tenant.** O `tenantId` entra nos logs estruturados via MDC (RNF-09).
8. **Defesa em camadas.** Além do RLS, a aplicação filtra por tenant usando `@TenantId` e `CurrentTenantIdentifierResolver` do Hibernate 6. O RLS é a garantia; o filtro da aplicação é a segunda barreira.
9. **Testes com PostgreSQL real.** Os testes de isolamento e de cobertura de RLS rodam com PostgreSQL real via Testcontainers, nunca H2.
10. **Falha fechada.** As políticas têm `USING` e `WITH CHECK`. Sem `app.tenant_id` definido, nenhuma linha é lida nem gravada.
11. **Origem do tenant.** O tenant vem do token autenticado (ver ADR-0002), nunca de parâmetro, corpo ou URL enviados pelo cliente.
12. **Tabelas de plataforma.** Tabelas sem tenant são exceções explícitas ao RLS e ficam listadas nesta ADR. Lista inicial: `tenants` e as credenciais de usuário (nomes finais conforme o modelo de dados; qualquer inclusão na lista exige atualizar esta ADR). O teste de cobertura de RLS as ignora por **lista nominal**, nunca por padrão de nome. O acesso a elas para autenticar, antes de o tenant ser conhecido, será decidido na ADR-0002 (ex.: função `SECURITY DEFINER` ou tenant pelo subdomínio).
13. **Acesso sempre transacional.** `spring.jpa.open-in-view=false`; todo acesso a dados ocorre em métodos `@Transactional` (`readOnly = true` quando for leitura); e uma consulta que rode sem `app.tenant_id` definido gera log de alerta.

## Consequências

- **Positivas:**
  - Uma migração e um schema; provisionamento de tenant por inserção (RN-18).
  - Isolamento garantido no banco e testado automaticamente (RNF-01, RN-01).
  - Baixo consumo de recursos na máquina da cloud gratuita.
  - Duas barreiras independentes (RLS e filtro do Hibernate) reduzem o risco de um erro isolado expor dados.
- **Negativas / dívidas aceitas:**
  - Backup, restauração e exclusão de um tenant inteiro ficam por script (por linha, não por banco). A anonimização de cliente sob pedido (RNF-05) continua simples.
  - Um tenant muito grande pode afetar os demais. Se surgir tenant grande ou exigência contratual de isolamento físico, migrar esse tenant para schema ou banco próprio.
  - O contexto do tenant por transação é um ponto crítico: um defeito nele afeta todos os recursos. Por isso fica concentrado em um único lugar e coberto por teste.
  - Depende da ADR-0002: o tenant vem do token e a identificação dele precisa estar decidida lá (condição 11).
  - Tabelas de plataforma (`tenants`, credenciais de usuário) ficam fora do RLS (condição 12). O acesso a elas para autenticar, antes de o tenant ser conhecido, fica em aberto até a ADR-0002 (ex.: função `SECURITY DEFINER` ou tenant pelo subdomínio). Enquanto isso, essas tabelas são o ponto de maior atenção em revisão, porque o banco não as protege por tenant.
  - Com `open-in-view=false`, nenhum acesso preguiçoso (lazy) funciona fora de transação; o código precisa carregar o necessário dentro do método `@Transactional`.
- **O que precisa ser feito por causa desta decisão** (estórias técnicas propostas; não criadas no Jira):
  1. Criar os papéis de banco (aplicação sem `BYPASSRLS` e dono das migrações) e a conexão da aplicação com o papel restrito.
  2. Definir o padrão de migração: coluna `tenant_id`, `ENABLE`/`FORCE RLS` e política `USING`/`WITH CHECK` por tabela, como convenção ou template reutilizável.
  3. Implementar o ponto único que define `app.tenant_id` por transação (`SET LOCAL`), com falha fechada quando ausente.
  4. Configurar `@TenantId` e `CurrentTenantIdentifierResolver` do Hibernate 6, lendo o tenant do contexto de segurança.
  5. Obter o tenant do token autenticado e proibir tenant vindo de parâmetro, corpo ou URL (depende da ADR-0002).
  6. Infraestrutura de testes com Testcontainers e PostgreSQL real, usada por todos os testes de isolamento.
  7. Teste automático de cobertura de RLS: toda tabela com `tenant_id` tem RLS ativo, forçado e com política.
  8. Teste de isolamento reutilizável (tenants A e B) para aplicar nos recursos de E1 e E2.
  9. `tenantId` no MDC e nos logs estruturados (RNF-09).
  10. Script de provisionamento do tenant e do primeiro Gestor compatível com RLS (RN-18; SRV-6).
  11. Script de backup, restauração e exclusão por tenant (dívida aceita).
  12. Atualizar o HLD e o runbook com os papéis de banco e a regra do contexto de tenant.
  13. Definir a lista nominal de tabelas de plataforma e o acesso a elas na autenticação (depende da ADR-0002).
  14. Configurar `spring.jpa.open-in-view=false` e o log de alerta para consulta sem `app.tenant_id`, com o teste automático correspondente.

## Como verificar

- **Cobertura de RLS (automático, CI):** consulta ao catálogo do PostgreSQL, em PostgreSQL real via Testcontainers, falha se alguma tabela com `tenant_id` não tiver `relrowsecurity` e `relforcerowsecurity` ativos e ao menos uma política com `USING` e `WITH CHECK`. Só as tabelas da lista nominal de plataforma (condição 12) são ignoradas; tabela nova fora da lista sem RLS faz o teste falhar.
- **Acesso transacional (automático, CI):** teste de arquitetura (ex.: ArchUnit) falha se um método de repositório ou serviço de dados for chamado fora de `@Transactional`, ou se um método somente de leitura não for `readOnly = true`; teste de configuração falha se `spring.jpa.open-in-view` não for `false`; teste de integração confirma que uma consulta sem `app.tenant_id` definido gera o log de alerta.
- **Isolamento (automático, CI):** com os tenants A e B, conectado com o papel da aplicação, A não lê nem escreve linha de B (inclui tentativa de `INSERT`/`UPDATE` com `tenant_id` de B, barrada pelo `WITH CHECK`).
- **Falha fechada (automático):** sem `app.tenant_id` definido, `SELECT` retorna zero linhas e `INSERT` é rejeitado.
- **Sem vazamento no pool (automático):** duas requisições seguidas de tenants diferentes na mesma conexão não compartilham o contexto.
- **Papel da aplicação (automático):** o papel usado em runtime não é superuser, não tem `BYPASSRLS` e não é dono das tabelas.
- **Origem do tenant (automático):** requisição com `tenant` ou `tenant_id` em parâmetro, corpo ou URL diferente do token é ignorada ou rejeitada, e vale o tenant do token.
- **Revisão:** o agente revisor e a revisão humana conferem este checklist em toda estória que cria tabela ou acessa dados.
