// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** ADR-0001, condições 2, 6, 9 e 10, numa tabela criada pelo próprio modelo de migração. */
@SpringBootTest
class IsolamentoPorRlsTest extends ComPostgres {

    private static final Path MODELO = Path.of("src/main/resources/db/modelo/tabela_de_negocio.sql");
    private static final String TABELA = "exemplo_rls";
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID LINHA_A = UUID.randomUUID();
    private static final UUID LINHA_B = UUID.randomUUID();
    /** SQLSTATE de violação de política de RLS (e de privilégio insuficiente). */
    private static final String RECUSADO = "42501";

    private Connection app;

    @BeforeEach
    void criaATabelaPeloModelo() throws SQLException, IOException {
        try (var dono = conectar("servio_owner", SENHA_OWNER); var sql = dono.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS " + TABELA);
            sql.execute(Files.readString(MODELO).replace("__tabela__", TABELA));
        }
        app = conectar("servio_app", SENHA_APP);
        app.setAutoCommit(false);
        inserirComo(A, A, LINHA_A, "de-a");
        inserirComo(B, B, LINHA_B, "de-b");
    }

    @AfterEach
    void remove() throws SQLException {
        app.close();
        try (var dono = conectar("servio_owner", SENHA_OWNER); var sql = dono.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS " + TABELA);
        }
    }

    @Test
    void tenantSoLeAlteraEApagaAsProprias() throws SQLException {
        usar(A);
        assertThat(contar("select count(*) from " + TABELA)).isEqualTo(1);
        assertThat(contar("select count(*) from " + TABELA + " where id = '" + LINHA_B + "'")).isZero();
        assertThat(alterar("update " + TABELA + " set codigo = 'invadido' where id = '" + LINHA_B + "'")).isZero();
        assertThat(alterar("delete from " + TABELA + " where id = '" + LINHA_B + "'")).isZero();
        app.commit();

        usar(B);
        assertThat(contar("select count(*) from " + TABELA + " where codigo = 'de-b'")).isEqualTo(1);
    }

    /** Sem WHERE: não deixa a política de leitura mascarar as de alteração e exclusão. */
    @Test
    void alteracaoEExclusaoSemFiltroSoAtingemAsProprias() throws SQLException {
        usar(A);
        assertThat(alterar("update " + TABELA + " set codigo = 'novo'")).isEqualTo(1);
        assertThat(alterar("delete from " + TABELA)).isEqualTo(1);
        app.commit();

        usar(B);
        assertThat(contar("select count(*) from " + TABELA + " where codigo = 'de-b'")).isEqualTo(1);
    }

    @Test
    void naoGravaComOTenantDeOutro() throws SQLException {
        recusado(() -> inserirComo(A, B, UUID.randomUUID(), "forjado"));
        recusado(() -> {
            usar(A);
            alterar("update " + TABELA + " set tenant_id = '" + B + "' where id = '" + LINHA_A + "'");
        });
    }

    @Test
    void semTenantNadaEhLidoNemGravado() throws SQLException {
        // conexão nova: a variável nunca existiu
        try (var nova = conectar("servio_app", SENHA_APP); var sql = nova.createStatement()) {
            try (var linhas = sql.executeQuery("select count(*) from " + TABELA)) {
                linhas.next();
                assertThat(linhas.getInt(1)).isZero();
            }
            assertThatThrownBy(() -> sql.execute(insert(A, UUID.randomUUID(), "x")))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(RECUSADO));
        }
        // conexão reutilizada: já teve tenant em transações anteriores
        assertThat(contar("select count(*) from " + TABELA)).isZero();
        assertThat(alterar("update " + TABELA + " set codigo = 'y'")).isZero();
        assertThat(alterar("delete from " + TABELA)).isZero();
        app.rollback();
        recusado(() -> alterar(insert(A, UUID.randomUUID(), "x")));
    }

    @Test
    void indicesUnicosIncluemOTenant() throws SQLException {
        assertThat(contar("""
                select count(*) from pg_index i
                where i.indrelid = '%s'::regclass and i.indisunique
                  and not exists (select 1 from pg_attribute a
                                  where a.attrelid = i.indrelid and a.attname = 'tenant_id'
                                    and a.attnum = i.indkey[0])""".formatted(TABELA)))
                .isZero();
        // o mesmo código pode existir em tenants diferentes (as duas linhas já entraram), não no mesmo
        assertThatThrownBy(() -> inserirComo(A, A, UUID.randomUUID(), "de-a"))
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
    }

    @Test
    void aplicacaoSoTemOsPrivilegiosDeLinhaERlsEstaForcado() throws SQLException {
        assertThat(contar("""
                select count(*) from pg_class
                where oid = '%s'::regclass and relrowsecurity and relforcerowsecurity""".formatted(TABELA)))
                .isEqualTo(1);
        // conjunto exato: TRUNCATE e REFERENCES ignoram o RLS, TRIGGER permite código na tabela
        assertThat(contar("""
                select count(*) from pg_class c, aclexplode(c.relacl) a
                where c.oid = '%1$s'::regclass and a.grantee in (0, 'servio_app'::regrole)
                  and a.privilege_type not in ('SELECT', 'INSERT', 'UPDATE', 'DELETE')""".formatted(TABELA)))
                .isZero();
        assertThat(contar("""
                select count(*) from pg_class c, aclexplode(c.relacl) a
                where c.oid = '%s'::regclass and a.grantee = 'servio_app'::regrole""".formatted(TABELA)))
                .isEqualTo(4);
        // o PostgreSQL reaproveitaria o USING; a ADR pede o WITH CHECK escrito
        assertThat(contar("""
                select count(*) from pg_policy
                where polrelid = '%s'::regclass and polqual is not null and polwithcheck is not null"""
                        .formatted(TABELA)))
                .isEqualTo(1);
    }

    private static Connection conectar(String papel, String senha) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), papel, senha);
    }

    private void usar(UUID tenant) throws SQLException {
        try (var sql = app.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            sql.setString(1, tenant.toString());
            sql.execute();
        }
    }

    private static String insert(UUID tenant, UUID id, String codigo) {
        return "insert into %s (tenant_id, id, codigo) values ('%s', '%s', '%s')".formatted(TABELA, tenant, id, codigo);
    }

    private void inserirComo(UUID contexto, UUID dono, UUID id, String codigo) throws SQLException {
        try {
            usar(contexto);
            alterar(insert(dono, id, codigo));
            app.commit();
        } catch (SQLException e) {
            app.rollback();
            throw e;
        }
    }

    private int alterar(String comando) throws SQLException {
        try (var sql = app.createStatement()) {
            return sql.executeUpdate(comando);
        }
    }

    private int contar(String consulta) throws SQLException {
        try (var sql = app.createStatement(); var linhas = sql.executeQuery(consulta)) {
            linhas.next();
            return linhas.getInt(1);
        }
    }

    private interface Acao {
        void executar() throws SQLException;
    }

    private void recusado(Acao acao) throws SQLException {
        assertThatThrownBy(acao::executar)
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(RECUSADO));
        app.rollback();
    }
}
