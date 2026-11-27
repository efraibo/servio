// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.servio.tenant.Gravacao;
import app.servio.tenant.GravacaoSemEfeitoException;
import app.servio.tenant.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ADR-0001, condições 3 e 13; ADR-0002, regra 15. */
@SpringBootTest
class TenantNaTransacaoTest extends ComPostgres {

    private static final String TABELA = "exemplo_ponto_unico";
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager gerente;

    @Autowired
    private Environment ambiente;

    @BeforeEach
    void cria() throws SQLException, IOException {
        comoDono("DROP TABLE IF EXISTS " + TABELA);
        comoDono(Files.readString(Path.of("src/main/resources/db/modelo/tabela_de_negocio.sql"))
                .replace("__tabela__", TABELA));
    }

    @AfterEach
    void remove() throws SQLException {
        comoDono("DROP TABLE IF EXISTS " + TABELA);
    }

    @Test
    void todaTransacaoRecebeOTenantInclusiveRequiresNew() {
        var nova = new TransactionTemplate(gerente);
        nova.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        TenantContext.executarComo(A, () -> transacao().execute(externa -> {
            assertThat(tenantNoBanco()).isEqualTo(A.toString());
            var pidExterno = pid();
            nova.executeWithoutResult(interna -> {
                assertThat(pid()).isNotEqualTo(pidExterno);
                assertThat(tenantNoBanco()).isEqualTo(A.toString());
            });
            assertThat(tenantNoBanco()).isEqualTo(A.toString());
            return null;
        }));
    }

    @Test
    void contextoNaoPassaDeUmaTransacaoParaOutraNaMesmaConexao() {
        var primeiro = TenantContext.executarComo(A, () -> transacao().execute(s -> {
            gravar(A, "de-a");
            return pid();
        }));
        var segundo = TenantContext.executarComo(B, () -> transacao().execute(s -> {
            assertThat(tenantNoBanco()).isEqualTo(B.toString());
            assertThat(contar()).isZero();
            return pid();
        }));
        var semTenant = transacao().execute(s -> {
            assertThat(tenantNoBanco()).isNullOrEmpty();
            assertThat(contar()).isZero();
            return pid();
        });

        assertThat(segundo).isEqualTo(primeiro);
        assertThat(semTenant).isEqualTo(primeiro);
    }

    @Test
    void semTenantOBancoRecusaAGravacao() {
        assertThatThrownBy(() -> transacao().executeWithoutResult(s -> gravar(A, "x")))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void gravacaoSemEfeitoPorFaltaDeTenantEhErroExplicito() {
        TenantContext.executarComo(A, () -> transacao().execute(s -> {
            gravar(A, "de-a");
            return null;
        }));
        var atualizar = "update " + TABELA + " set codigo = 'y'";

        assertThatThrownBy(() -> transacao().executeWithoutResult(s -> Gravacao.exigirLinhas(jdbc.update(atualizar))))
                .isInstanceOf(GravacaoSemEfeitoException.class)
                .hasMessageContaining("sem tenant");
        TenantContext.executarComo(A, () -> transacao().execute(s -> {
            assertThat(Gravacao.exigirLinhas(jdbc.update(atualizar))).isEqualTo(1);
            return null;
        }));
    }

    @Test
    void naoTrocaDeTenantDentroDeUmaTransacao() {
        // a variável do banco já foi definida no início: trocar aqui não teria efeito
        assertThatThrownBy(() -> TenantContext.executarComo(A, () -> transacao().execute(
                        s -> TenantContext.executarComo(B, () -> null))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void foraDoEscopoNaoHaTenant() {
        TenantContext.executarComo(A, () -> {
            assertThat(TenantContext.atual()).contains(A);
            return null;
        });
        assertThat(TenantContext.atual()).isEmpty();
    }

    @Test
    void tenantNuloEhRecusado() {
        assertThatThrownBy(() -> TenantContext.executarComo(null, () -> null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Falha real no set_config: o superusuário tira o EXECUTE e devolve em seguida. */
    @Test
    void falhaAoDefinirOTenantNaoDeixaTransacaoAberta() throws SQLException {
        comoSuperusuario("REVOKE EXECUTE ON FUNCTION set_config(text, text, boolean) FROM PUBLIC");
        try {
            assertThatThrownBy(() -> TenantContext.executarComo(A, () -> transacao().execute(s -> null)))
                    .hasStackTraceContaining("permission denied");
        } finally {
            comoSuperusuario("GRANT EXECUTE ON FUNCTION set_config(text, text, boolean) TO PUBLIC");
        }

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        assertThat(jdbc.getDataSource().unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections())
                .isZero();
        TenantContext.executarComo(B, () -> transacao().execute(s -> {
            assertThat(s.isNewTransaction()).isTrue();
            assertThat(tenantNoBanco()).isEqualTo(B.toString());
            return null;
        }));
    }

    private static void comoSuperusuario(String comando) throws SQLException {
        try (var admin = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var sql = admin.createStatement()) {
            sql.execute(comando);
        }
    }

    @Test
    void transacaoSomenteLeituraTambemRecebeOTenant() {
        var leitura = transacao();
        leitura.setReadOnly(true);

        TenantContext.executarComo(A, () -> leitura.execute(s -> {
            assertThat(jdbc.queryForObject("show transaction_read_only", String.class)).isEqualTo("on");
            assertThat(tenantNoBanco()).isEqualTo(A.toString());
            return null;
        }));
    }

    @Test
    void naoTrocaDeTenantEmEscopoSupportsSemTransacao() {
        var supports = transacao();
        supports.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);

        // a sessão do Hibernate é reaproveitada no escopo, com o tenant de quando foi aberta
        assertThatThrownBy(() -> TenantContext.executarComo(A, () -> supports.execute(
                        s -> TenantContext.executarComo(B, () -> null))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uuidZeroEhRecusado() {
        assertThatThrownBy(() -> TenantContext.executarComo(new UUID(0, 0), () -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openInViewDesligado() {
        assertThat(ambiente.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    private TransactionTemplate transacao() {
        return new TransactionTemplate(gerente);
    }

    private String tenantNoBanco() {
        return jdbc.queryForObject("select current_setting('app.tenant_id', true)", String.class);
    }

    private Integer pid() {
        return jdbc.queryForObject("select pg_backend_pid()", Integer.class);
    }

    private Integer contar() {
        return jdbc.queryForObject("select count(*) from " + TABELA, Integer.class);
    }

    private void gravar(UUID tenant, String codigo) {
        jdbc.update("insert into " + TABELA + " (tenant_id, id, codigo) values (?, ?, ?)",
                tenant, UUID.randomUUID(), codigo);
    }

    private static void comoDono(String comando) throws SQLException {
        try (var dono = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER);
                var sql = dono.createStatement()) {
            sql.execute(comando);
        }
    }
}
