// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.servio.tenant.TenantContext;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SRV-21, cenário 6 (RN-01): o papel da aplicação só vê as credenciais do tenant do contexto. */
@SpringBootTest
class CredencialSobRlsTest extends ComPostgres {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager gerente;

    @BeforeEach
    void cria() throws SQLException {
        limpar();
        oficina(A, "credencial-a");
        oficina(B, "credencial-b");
        como(A, () -> gravar(A, "gestor-a@oficina.com"));
        como(B, () -> gravar(B, "gestor-b@oficina.com"));
    }

    @AfterEach
    void remove() throws SQLException {
        limpar();
    }

    @Test
    void cadaTenantLeSoASuaESemTenantNadaVem() {
        assertThat(como(A, this::emails)).containsExactly("gestor-a@oficina.com");
        assertThat(como(B, this::emails)).containsExactly("gestor-b@oficina.com");
        List<String> semTenant = new TransactionTemplate(gerente).execute(s -> emails());
        assertThat(semTenant).isEmpty();
    }

    @Test
    void gravarComOutroTenantEhRejeitado() {
        assertThatThrownBy(() -> como(A, () -> gravar(B, "invasor@oficina.com")))
                .hasStackTraceContaining("row-level security");
    }

    private int gravar(UUID tenant, String email) {
        return jdbc.update(
                "insert into usuarios (tenant_id, id, email, senha_hash, perfil) values (?, ?, ?, 'x', 'GESTOR')",
                tenant, UUID.randomUUID(), email);
    }

    private List<String> emails() {
        return jdbc.queryForList("select email from usuarios", String.class);
    }

    private <T> T como(UUID tenant, Supplier<T> acao) {
        return TenantContext.executarComo(tenant, () -> new TransactionTemplate(gerente).execute(s -> acao.get()));
    }

    private static void oficina(UUID id, String slug) throws SQLException {
        comoDono("insert into tenants (id, nome, slug, cor_primaria, cor_secundaria) values ('" + id
                + "', 'Oficina', '" + slug + "', '#000000', '#FFFFFF')");
    }

    private static void limpar() throws SQLException {
        comoDono("truncate tenants cascade");
    }

    private static void comoDono(String comando) throws SQLException {
        try (var dono = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER);
                var sql = dono.createStatement()) {
            sql.execute(comando);
        }
    }
}
