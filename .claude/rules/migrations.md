---
paths:
  - "backend/src/main/resources/db/migration/**"
---
# 🟣 Migrações (ADR-0001)
Flyway, SQL puro. Toda tabela de negócio, na mesma migração:
- coluna `tenant_id`;
- `ENABLE` e `FORCE ROW LEVEL SECURITY`;
- política com `USING` e `WITH CHECK` baseada em `current_setting('app.tenant_id')`;
- índices únicos incluindo `tenant_id`.
Sem RLS só `tenants`, `spring_session`, `spring_session_attributes`; incluir outra exige alterar a ADR.
Migrações rodam com o papel dono; a aplicação usa o papel restrito.
