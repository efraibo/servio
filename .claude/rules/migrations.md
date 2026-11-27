---
paths:
  - "backend/src/main/resources/db/migration/**"
---
# 🟣 Migrações (ADR-0001)
Flyway, SQL puro. Parta do modelo `db/modelo/tabela_de_negocio.sql`. Toda tabela de negócio, na mesma migração:
- coluna `tenant_id`;
- `ENABLE` e `FORCE ROW LEVEL SECURITY`;
- política com `USING` e `WITH CHECK` baseada em `current_setting('app.tenant_id')`;
- índices únicos começando por `tenant_id`;
- chave estrangeira entre tabelas de negócio com `tenant_id` (a checagem de FK ignora o RLS).
Sem RLS só `tenants`, `spring_session`, `spring_session_attributes`; incluir outra exige alterar a ADR.
Migrações rodam com o papel dono; a aplicação usa o papel restrito.
