// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import app.servio.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ADR-0001, condições 12 e 13. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class AlertaSemTenantTest extends ComPostgres {

    private static final String ALERTA = "consulta sem app.tenant_id";

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entidades;

    @Autowired
    private PlatformTransactionManager gerente;

    @BeforeEach
    void cria() throws SQLException, IOException {
        remove();
        comoDono(Files.readString(Path.of("src/main/resources/db/modelo/tabela_de_negocio.sql"))
                .replace("__tabela__", "exemplo_alerta"));
        // tenants já existe (SRV-6)
        for (var tabela : new String[] {"spring_session", "spring_session_attributes"}) {
            comoDono("CREATE TABLE " + tabela + " (id uuid); GRANT SELECT ON " + tabela + " TO servio_app");
        }
    }

    @AfterEach
    void remove() throws SQLException {
        comoDono("DROP TABLE IF EXISTS exemplo_alerta, spring_session, spring_session_attributes");
    }

    @Test
    void jdbcSemTenantAlerta(CapturedOutput saida) {
        jdbc.queryForList("select codigo from exemplo_alerta where codigo = 'segredo-do-teste'");

        assertThat(saida).contains(ALERTA, "exemplo_alerta").doesNotContain("segredo-do-teste");
    }

    @Test
    void jpaSemTenantAlerta(CapturedOutput saida) {
        new TransactionTemplate(gerente).execute(s -> entidades
                .createNativeQuery("select codigo from public.exemplo_alerta")
                .getResultList());

        assertThat(saida).contains(ALERTA);
    }

    @Test
    void tenantForaDeTransacaoNaoChegaAoBancoEAlerta(CapturedOutput saida) {
        TenantContext.executarComo(UUID.randomUUID(), () -> jdbc.queryForList("select 1 from exemplo_alerta"));

        assertThat(saida).contains(ALERTA);
    }

    @Test
    void comTenantNaoAlerta(CapturedOutput saida) {
        TenantContext.executarComo(UUID.randomUUID(), () -> new TransactionTemplate(gerente)
                .execute(s -> jdbc.queryForList("select codigo from exemplo_alerta")));

        assertThat(saida).doesNotContain(ALERTA);
    }

    @Test
    void tabelasDePlataformaNaoAlertam(CapturedOutput saida) {
        jdbc.queryForList("select * from tenants t join public.spring_session s on true");
        jdbc.queryForList("select * from \"spring_session_attributes\"");
        jdbc.queryForList("select 1");

        assertThat(saida).doesNotContain(ALERTA);
    }

    @Test
    void plataformaJuntoComNegocioAlerta(CapturedOutput saida) {
        jdbc.queryForList("select 1 from tenants join exemplo_alerta on true");

        assertThat(saida).contains(ALERTA);
    }

    @Test
    void literaisLongosOuEspeciaisNaoDerrubamNemVazam(CapturedOutput saida) {
        var longo = "x".repeat(100_000);

        jdbc.queryForList("select 1 from exemplo_alerta where codigo = '" + longo + "'");
        jdbc.queryForList("select 1 from exemplo_alerta where codigo = $$ from vazou1 $$");
        jdbc.queryForList("select 1 from exemplo_alerta where codigo = E'a\\' from vazou2'");
        jdbc.queryForList("select 1 /* a /* b */ from vazou3 */ from exemplo_alerta");

        assertThat(saida).contains(ALERTA).doesNotContain("vazou", "não foi possível");
    }

    @Test
    void funcaoNaListaDoFromNaoViraTabela(CapturedOutput saida) {
        jdbc.queryForList("select 1 from tenants t, generate_series(1, 2) g");

        assertThat(saida).doesNotContain(ALERTA);
    }

    private static void comoDono(String comando) throws SQLException {
        try (var dono = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER);
                var sql = dono.createStatement()) {
            sql.execute(comando);
        }
    }
}
