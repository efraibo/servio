-- 🔵 Só quem recebeu GRANT explícito usa o schema (os papéis vêm de infra/postgres/init).
REVOKE ALL ON SCHEMA public FROM PUBLIC;
