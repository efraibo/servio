// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.servio.tenant.TenantContext;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ADR-0001, condição 8: o Hibernate filtra por tenant e o RLS continua sendo a garantia. */
@SpringBootTest
class FiltroDoHibernateTest extends ComPostgres {

    @Entity(name = "Exemplo")
    @Table(name = "exemplo_hibernate")
    static class Exemplo {

        @Id
        UUID id;

        @TenantId
        UUID tenantId;

        String codigo;

        Exemplo() {}

        Exemplo(String codigo) {
            this.id = UUID.randomUUID();
            this.codigo = codigo;
        }
    }

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @PersistenceContext
    private EntityManager entidades;

    @Autowired
    private PlatformTransactionManager gerente;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID linhaDeB;

    @BeforeEach
    void cria() throws SQLException, IOException {
        comoDono("DROP TABLE IF EXISTS exemplo_hibernate");
        comoDono(Files.readString(Path.of("src/main/resources/db/modelo/tabela_de_negocio.sql"))
                .replace("__tabela__", "exemplo_hibernate"));
        gravar(A, "de-a");
        linhaDeB = gravar(B, "de-b").id;
    }

    @AfterEach
    void remove() throws SQLException {
        comoDono("DROP TABLE IF EXISTS exemplo_hibernate");
    }

    @Test
    void gravacaoPreencheOTenantEConsultaSoTrazAsProprias() {
        como(A, () -> {
            assertThat(codigos()).containsExactly("de-a");
            assertThat(entidades.find(Exemplo.class, linhaDeB)).isNull();
            return null;
        });
        var tenantGravado = como(B, () -> jdbc.queryForObject(
                "select tenant_id from exemplo_hibernate where codigo = 'de-b'", UUID.class));
        assertThat(tenantGravado).isEqualTo(B);
    }

    /** Sem o RLS, o filtro da aplicação ainda segura: é a segunda barreira, independente. */
    @Test
    void filtroDoHibernateValeMesmoSemRls() throws SQLException {
        comoDono("ALTER TABLE exemplo_hibernate DISABLE ROW LEVEL SECURITY");

        como(A, () -> {
            assertThat(codigos()).containsExactly("de-a");
            assertThat(entidades.find(Exemplo.class, linhaDeB)).isNull();
            return null;
        });
    }

    @Test
    void consultaNativaContinuaProtegidaPeloRls() {
        List<?> nativa = como(A, () -> entidades
                .createNativeQuery("select codigo from exemplo_hibernate", String.class)
                .getResultList());
        assertThat(nativa).isEqualTo(List.of("de-a"));
    }

    @Test
    void semTenantNadaEhLidoNemGravado() {
        var transacao = new TransactionTemplate(gerente);

        List<String> lidos = transacao.execute(s -> codigos());
        assertThat(lidos).isEmpty();
        assertThatThrownBy(() -> transacao.executeWithoutResult(s -> {
                    entidades.persist(new Exemplo("orfa"));
                    entidades.flush();
                }))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void jdbcEJpaUsamAMesmaTransacao() {
        como(A, () -> {
            entidades.persist(new Exemplo("junto"));
            entidades.flush();
            assertThat(jdbc.queryForObject("select count(*) from exemplo_hibernate", Integer.class)).isEqualTo(2);
            return null;
        });
    }

    private Exemplo gravar(UUID tenant, String codigo) {
        return como(tenant, () -> {
            var exemplo = new Exemplo(codigo);
            entidades.persist(exemplo);
            return exemplo;
        });
    }

    private List<String> codigos() {
        return entidades.createQuery("select e.codigo from Exemplo e", String.class).getResultList();
    }

    private <T> T como(UUID tenant, Supplier<T> acao) {
        return TenantContext.executarComo(tenant, () -> new TransactionTemplate(gerente).execute(s -> acao.get()));
    }

    private static void comoDono(String comando) throws SQLException {
        try (var dono = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER);
                var sql = dono.createStatement()) {
            sql.execute(comando);
        }
    }
}
