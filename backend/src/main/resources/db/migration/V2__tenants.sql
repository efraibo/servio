-- 🔵 Tabela de plataforma, sem RLS (ADR-0001, condição 12): só dados públicos (ADR-0002, regra 6).
-- O logo entra com a ADR de armazenamento (RN-17).
CREATE TABLE tenants (
    id             uuid    PRIMARY KEY,
    nome           text    NOT NULL CHECK (btrim(nome) <> ''),
    slug           text    NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9-]+$'),   -- RN-24
    cor_primaria   text    NOT NULL CHECK (cor_primaria ~ '^#[0-9A-Fa-f]{6}$'),   -- RN-17
    cor_secundaria text    NOT NULL CHECK (cor_secundaria ~ '^#[0-9A-Fa-f]{6}$'),
    ativo          boolean NOT NULL DEFAULT true
);

-- A aplicação só lê. Quem cria é o provisionamento, com o papel dono (RN-18).
GRANT SELECT ON tenants TO servio_app;
