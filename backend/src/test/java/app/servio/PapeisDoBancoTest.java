// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** ADR-0001, condição 1, conferida pela conexão que a aplicação de fato usa. */
@SpringBootTest
class PapeisDoBancoTest extends ComPostgres {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aplicacaoConectaComOPapelRestrito() {
        assertThat(texto("select session_user::text")).isEqualTo("servio_app");
        assertThat(texto("""
                select (rolsuper or rolbypassrls or rolreplication or rolcreatedb or rolcreaterole)::text
                from pg_roles where rolname = current_user"""))
                .isEqualTo("false");
        assertThat(texto("select count(*)::text from pg_auth_members where member = current_user::regrole"))
                .isEqualTo("0");
    }

    @Test
    void migracoesRodamComODonoEAAplicacaoNaoEhDonaDeNada() {
        var donos = jdbc.queryForList("""
                select distinct r.rolname::text from pg_class c
                join pg_roles r on r.oid = c.relowner
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public'""", String.class);
        assertThat(donos).containsExactly("servio_owner");
        assertThat(texto("select nspowner::regrole::text from pg_namespace where nspname = 'public'"))
                .isEqualTo("servio_owner");
    }

    @Test
    void schemaSoAceitaGrantExplicito() {
        assertThat(texto("""
                select count(*)::text from pg_namespace n, aclexplode(n.nspacl) a
                where n.nspname = 'public' and a.grantee = 0"""))
                .isEqualTo("0");
        assertThat(texto("select has_schema_privilege(current_user, 'public', 'create')::text"))
                .isEqualTo("false");
    }

    @Test
    void bancoEhPostgres18() {
        assertThat(texto("select (current_setting('server_version_num')::int / 10000)::text")).isEqualTo("18");
    }

    private String texto(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
