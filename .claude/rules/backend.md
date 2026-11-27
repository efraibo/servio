---
paths:
  - "backend/**"
---
# 🟣 Backend (ADR-0003, ADR-0001, ADR-0002)
- Só `app.servio.<módulo>`: tenant, acesso, cadastros, agenda, os, painel.
- Dependências: acesso e cadastros → tenant; agenda → cadastros; os → cadastros e API pública de agenda; painel → agenda, os.
- Domínio em português, infraestrutura em inglês.
- Acesso a dados só em `@Transactional` (leitura com `readOnly = true`); só JPA ou consulta parametrizada.
- Tenant vem da sessão; `app.tenant_id` é definido em um único ponto (`app.servio.tenant`): use `TenantContext.executarComo` por fora da transação.
- Alteração ou exclusão que precisa ter efeito passa por `Gravacao.exigirLinhas`.
- Fora de `acesso`, dependa da interface `Autenticador`.
