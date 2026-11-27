-- 🔵 Modelo de migração para tabela de negócio (ADR-0001, condições 2, 6 e 10).
-- Como usar: copie para db/migration/V<n>__<nome>.sql, troque __tabela__ e a coluna "codigo".
-- O IsolamentoPorRlsTest executa este arquivo: o que está aqui é o que foi provado.
--
-- Regras que o modelo não escreve por você:
--   * toda restrição ou índice único começa por tenant_id;
--   * chave estrangeira entre tabelas de negócio leva tenant_id (a checagem de FK ignora o RLS);
--   * FORCE vale também para o dono: migração que mexe em dados precisa definir app.tenant_id.

CREATE TABLE __tabela__ (
    tenant_id uuid NOT NULL,
    id        uuid NOT NULL,
    codigo    text NOT NULL, -- exemplo: troque pelas colunas da tabela
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, codigo)
);

ALTER TABLE __tabela__ ENABLE ROW LEVEL SECURITY;
ALTER TABLE __tabela__ FORCE ROW LEVEL SECURITY;

-- Falha fechada: variável ausente ou vazia vira NULL, que não casa com linha nenhuma.
CREATE POLICY por_tenant ON __tabela__
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Só operações de linha. TRUNCATE ignora o RLS e não é concedido.
GRANT SELECT, INSERT, UPDATE, DELETE ON __tabela__ TO servio_app;
