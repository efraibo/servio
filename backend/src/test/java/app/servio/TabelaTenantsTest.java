// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** SRV-6, cenário 5: tabela de plataforma, sem RLS, só com dados públicos (ADR-0002, regra 6). */
@SpringBootTest
class TabelaTenantsTest extends ComPostgres {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void temSoOsDadosPublicos() {
        assertThat(jdbc.queryForList("""
                select attname::text from pg_attribute
                where attrelid = 'public.tenants'::regclass and attnum > 0 and not attisdropped""", String.class))
                .containsExactlyInAnyOrder("id", "nome", "slug", "cor_primaria", "cor_secundaria", "ativo");
    }

    @Test
    void aplicacaoSoLe() {
        assertThat(jdbc.queryForList("""
                select a.privilege_type from pg_class c, aclexplode(c.relacl) a
                where c.oid = 'public.tenants'::regclass and a.grantee in (0, 'servio_app'::regrole)""",
                String.class))
                .containsExactly("SELECT");
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_attribute
                where attrelid = 'public.tenants'::regclass and attacl is not null""", Integer.class))
                .isZero();
    }

    /** As mesmas regras no banco, para quem não passar pelo comando. */
    @ParameterizedTest
    @CsvSource({
        "'  ', ok,    '#000000', '#000000'",
        "Nome, Maiu,  '#000000', '#000000'",
        "Nome, a_b,   '#000000', '#000000'",
        "Nome, ok,    azul,      '#000000'",
        "Nome, ok,    '#000000', '#12345'"
    })
    void bancoTambemRecusaDadoInvalido(String nome, String slug, String primaria, String secundaria)
            throws SQLException {
        try (var dono = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER);
                var sql = dono.prepareStatement(
                        "insert into tenants (id, nome, slug, cor_primaria, cor_secundaria) values (?, ?, ?, ?, ?)")) {
            dono.setAutoCommit(false);
            sql.setObject(1, UUID.randomUUID());
            sql.setString(2, nome);
            sql.setString(3, slug);
            sql.setString(4, primaria);
            sql.setString(5, secundaria);

            assertThatThrownBy(sql::executeUpdate)
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            dono.rollback();
        }
    }
}
