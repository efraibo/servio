-- 🟢 Única definição dos papéis (ADR-0001, condição 1). Roda uma vez, via psql, na inicialização da imagem.
\getenv owner_password SERVIO_OWNER_PASSWORD
\getenv app_password SERVIO_APP_PASSWORD

-- Dono do schema: roda as migrações.
CREATE ROLE servio_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'owner_password';
-- Aplicação: restrito. Os privilégios em tabelas são concedidos pelas migrações.
CREATE ROLE servio_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'app_password';

ALTER SCHEMA public OWNER TO servio_owner;
GRANT USAGE ON SCHEMA public TO servio_app;
