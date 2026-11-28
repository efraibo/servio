-- 🔵 Usuários da oficina com a credencial (ADR-0002, regra 8), pelo modelo de tabela de negócio.
CREATE TABLE usuarios (
    tenant_id  uuid    NOT NULL REFERENCES tenants (id),
    id         uuid    NOT NULL,
    email      text    NOT NULL CHECK (btrim(email) <> '' AND email = lower(email)),   -- RN-22
    senha_hash text    NOT NULL,
    perfil     text    NOT NULL CHECK (perfil IN ('GESTOR', 'ATENDENTE', 'MECANICO')),
    ativo      boolean NOT NULL DEFAULT true,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, email)
);

ALTER TABLE usuarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuarios FORCE ROW LEVEL SECURITY;

-- Falha fechada: variável ausente ou vazia vira NULL, que não casa com linha nenhuma.
CREATE POLICY por_tenant ON usuarios
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Só operações de linha. TRUNCATE ignora o RLS e não é concedido.
GRANT SELECT, INSERT, UPDATE, DELETE ON usuarios TO servio_app;
