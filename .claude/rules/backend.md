---
paths:
  - "backend/**"
---
# 🟣 Backend (ADR-0003, ADR-0001, ADR-0002)
- Só `app.servio.<módulo>`: tenant, acesso, cadastros, agenda, os, painel.
- Dependências: acesso e cadastros → tenant; agenda → cadastros; os → cadastros e API pública de agenda; painel → agenda, os.
- Domínio em português, infraestrutura em inglês.
- Acesso a dados só em `@Transactional` (leitura com `readOnly = true`); só JPA ou consulta parametrizada.
- Tenant vem da sessão; `app.tenant_id` é definido em um único ponto.
- Fora de `acesso`, dependa da interface `Autenticador`.
