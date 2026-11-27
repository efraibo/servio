// 🔵
package app.servio;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lê o catálogo e lista o que está sem o isolamento da ADR-0001 (condições 2, 4 e 12). */
final class CoberturaDeRls {

    /** Lista nominal da ADR-0001, condição 12, mais o histórico do Flyway. Sempre no schema public. */
    private static final Set<String> PLATAFORMA = Set.of(
            "public.tenants",
            "public.spring_session",
            "public.spring_session_attributes",
            "public.flyway_schema_history");

    private static final String RELACOES = """
            select n.nspname || '.' || c.relname, c.relkind::text, c.relrowsecurity, c.relforcerowsecurity,
                   exists (select 1 from pg_attribute a
                           where a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped),
                   c.oid::bigint,
                   has_table_privilege('servio_app', c.oid, 'TRUNCATE, REFERENCES, TRIGGER')
            from pg_class c join pg_namespace n on n.oid = c.relnamespace
            where c.relkind in ('r', 'p', 'm', 'f')
              and n.nspname <> 'information_schema' and n.nspname !~ '^pg_'
            order by 1""";

    private static final String POLITICAS = """
            select p.polname, p.polcmd::text,
                   pg_get_expr(p.polqual, p.polrelid), pg_get_expr(p.polwithcheck, p.polrelid),
                   p.polpermissive and p.polroles = '{0}'
            from pg_policy p
            where p.polrelid = ?::bigint::oid
            order by 1""";

    private record Politica(String nome, String comando, String using, String withCheck, boolean valeParaTodos) {}

    private CoberturaDeRls() {}

    /** @param modelo SQL do modelo de migração; a expressão da política dele é a única aceita. */
    static List<String> violacoes(Connection dono, String modelo) throws SQLException {
        var expressao = expressaoDoModelo(dono, modelo);
        var violacoes = new ArrayList<String>();
        try (var sql = dono.createStatement(); var r = sql.executeQuery(RELACOES)) {
            while (r.next()) {
                var nome = r.getString(1);
                if (PLATAFORMA.contains(nome)) {
                    continue;
                }
                if (Set.of("m", "f").contains(r.getString(2))) {
                    violacoes.add(nome + ": view materializada ou tabela estrangeira não tem RLS");
                } else if (!r.getBoolean(5)) {
                    violacoes.add(nome + ": sem tenant_id e fora da lista de plataforma");
                } else {
                    if (!r.getBoolean(3)) {
                        violacoes.add(nome + ": RLS não ativo");
                    }
                    if (!r.getBoolean(4)) {
                        violacoes.add(nome + ": RLS não forçado");
                    }
                    if (r.getBoolean(7)) {
                        violacoes.add(nome + ": servio_app tem privilégio que ignora o RLS");
                    }
                    conferirPoliticas(dono, nome, r.getLong(6), expressao, violacoes);
                }
            }
        }
        return violacoes;
    }

    private static void conferirPoliticas(
            Connection dono, String tabela, long oid, String expressao, List<String> violacoes) throws SQLException {
        var politicas = politicas(dono, oid);
        // '*' = todos os comandos
        if (politicas.stream().noneMatch(p ->
                p.comando().equals("*") && p.valeParaTodos() && p.using() != null && p.withCheck() != null)) {
            violacoes.add(tabela + ": sem política com USING e WITH CHECK");
            return;
        }
        for (var p : politicas) {
            if (differe(p.using(), expressao) || differe(p.withCheck(), expressao)) {
                violacoes.add(tabela + ": política " + p.nome() + " difere do modelo");
            }
        }
    }

    private static boolean differe(String encontrada, String esperada) {
        return encontrada != null && !encontrada.equals(esperada);
    }

    private static List<Politica> politicas(Connection dono, long oid) throws SQLException {
        try (var sql = dono.prepareStatement(POLITICAS)) {
            sql.setLong(1, oid);
            try (var r = sql.executeQuery()) {
                var politicas = new ArrayList<Politica>();
                while (r.next()) {
                    politicas.add(new Politica(
                            r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getBoolean(5)));
                }
                return politicas;
            }
        }
    }

    /** Cria uma tabela pelo modelo, lê a expressão como o PostgreSQL a guarda e desfaz. */
    private static String expressaoDoModelo(Connection dono, String modelo) throws SQLException {
        var ponto = dono.setSavepoint();
        try (var sql = dono.createStatement()) {
            sql.execute(modelo.replace("__tabela__", "referencia_do_modelo"));
            try (var r = sql.executeQuery("select 'public.referencia_do_modelo'::regclass::oid::bigint")) {
                r.next();
                return politicas(dono, r.getLong(1)).getFirst().using();
            }
        } finally {
            dono.rollback(ponto);
        }
    }
}
