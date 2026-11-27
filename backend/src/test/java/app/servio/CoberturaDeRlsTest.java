// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** ADR-0001, condições 4 e 12. Os controles criam tabelas numa transação que é desfeita. */
@SpringBootTest
class CoberturaDeRlsTest extends ComPostgres {

    private static final Path MODELO = Path.of("src/main/resources/db/modelo/tabela_de_negocio.sql");

    private Connection dono;

    @BeforeEach
    void abre() throws SQLException {
        dono = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER);
        dono.setAutoCommit(false);
    }

    @AfterEach
    void desfaz() throws SQLException {
        dono.rollback();
        dono.close();
    }

    @Test
    void esquemaDasMigracoesEstaCoberto() throws SQLException, IOException {
        assertThat(violacoes()).isEmpty();
    }

    @Test
    void tabelaCriadaPeloModeloPassa() throws SQLException, IOException {
        peloModelo("conforme");
        assertThat(violacoes()).isEmpty();
    }

    @Test
    void semForceFalha() throws SQLException, IOException {
        peloModelo("t");
        executar("ALTER TABLE t NO FORCE ROW LEVEL SECURITY");
        assertThat(violacoes()).containsExactly("public.t: RLS não forçado");
    }

    @Test
    void semEnableFalha() throws SQLException, IOException {
        peloModelo("t");
        executar("ALTER TABLE t DISABLE ROW LEVEL SECURITY");
        assertThat(violacoes()).containsExactly("public.t: RLS não ativo");
    }

    @Test
    void semPoliticaFalha() throws SQLException, IOException {
        peloModelo("t");
        executar("DROP POLICY por_tenant ON t");
        assertThat(violacoes()).containsExactly("public.t: sem política com USING e WITH CHECK");
    }

    @Test
    void politicaSemWithCheckFalha() throws SQLException, IOException {
        peloModelo("t");
        executar("DROP POLICY por_tenant ON t");
        executar("CREATE POLICY so_using ON t USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)");
        assertThat(violacoes()).containsExactly("public.t: sem política com USING e WITH CHECK");
    }

    @Test
    void politicaAbertaAoLadoDaCorretaFalha() throws SQLException, IOException {
        peloModelo("t");
        // políticas permissivas somam: uma aberta anula as outras
        executar("CREATE POLICY aberta ON t FOR SELECT USING (true)");
        assertThat(violacoes()).containsExactly("public.t: política aberta difere do modelo");
    }

    @Test
    void tabelaSemTenantForaDaListaFalha() throws SQLException, IOException {
        executar("CREATE TABLE solta (id uuid)");
        assertThat(violacoes()).containsExactly("public.solta: sem tenant_id e fora da lista de plataforma");
    }

    @Test
    void tabelasDaListaNominalSaoIgnoradas() throws SQLException, IOException {
        for (var tabela : List.of("tenants", "spring_session", "spring_session_attributes")) {
            executar("CREATE TABLE " + tabela + " (id uuid)");
        }
        executar("CREATE TABLE tenants_extra (id uuid)");
        assertThat(violacoes()).containsExactly("public.tenants_extra: sem tenant_id e fora da lista de plataforma");
    }

    @Test
    void viewMaterializadaFalha() throws SQLException, IOException {
        executar("CREATE MATERIALIZED VIEW copia AS SELECT 1 AS tenant_id");
        assertThat(violacoes()).containsExactly("public.copia: view materializada ou tabela estrangeira não tem RLS");
    }

    private static final String EXPRESSAO = "tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid";

    @Test
    void withCheckAbertoFalha() throws SQLException, IOException {
        peloModelo("t");
        executar("ALTER POLICY por_tenant ON t WITH CHECK (true)");
        assertThat(violacoes()).containsExactly("public.t: política por_tenant difere do modelo");
    }

    @Test
    void politicaQueNaoCobreTodosOsComandosPapeisFalha() throws SQLException, IOException {
        for (var trecho : List.of(
                "FOR UPDATE USING (%1$s) WITH CHECK (%1$s)",
                "WITH CHECK (%1$s)",
                "AS RESTRICTIVE USING (%1$s) WITH CHECK (%1$s)",
                "TO servio_owner USING (%1$s) WITH CHECK (%1$s)")) {
            var ponto = dono.setSavepoint();
            peloModelo("t");
            executar("DROP POLICY por_tenant ON t");
            executar("CREATE POLICY p ON t " + trecho.formatted(EXPRESSAO));
            assertThat(violacoes()).as(trecho).containsExactly("public.t: sem política com USING e WITH CHECK");
            dono.rollback(ponto);
        }
    }

    @Test
    void privilegioQueIgnoraORlsFalha() throws SQLException, IOException {
        peloModelo("t");
        executar("GRANT TRUNCATE ON t TO servio_app");
        assertThat(violacoes()).containsExactly("public.t: servio_app tem privilégio que ignora o RLS");
    }

    @Test
    void tabelaParticionadaTambemEhConferida() throws SQLException, IOException {
        executar("CREATE TABLE p (tenant_id uuid) PARTITION BY LIST (tenant_id)");
        assertThat(violacoes()).contains("public.p: RLS não ativo");
    }

    /** Outro schema e tabela estrangeira só o superusuário cria; a transação é desfeita. */
    @Test
    void outroSchemaETabelaEstrangeiraSaoConferidos() throws SQLException, IOException {
        dono.close();
        dono = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dono.setAutoCommit(false);
        executar("CREATE SCHEMA outro");
        executar("CREATE TABLE outro.tenants (id uuid)");
        executar("CREATE EXTENSION postgres_fdw");
        executar("CREATE SERVER s FOREIGN DATA WRAPPER postgres_fdw");
        executar("CREATE FOREIGN TABLE externa (tenant_id uuid) SERVER s");

        assertThat(violacoes()).containsExactly(
                "outro.tenants: sem tenant_id e fora da lista de plataforma",
                "public.externa: view materializada ou tabela estrangeira não tem RLS");
    }

    private void peloModelo(String tabela) throws SQLException, IOException {
        executar(Files.readString(MODELO).replace("__tabela__", tabela));
    }

    private void executar(String comando) throws SQLException {
        try (var sql = dono.createStatement()) {
            sql.execute(comando);
        }
    }

    private List<String> violacoes() throws SQLException, IOException {
        return CoberturaDeRls.violacoes(dono, Files.readString(MODELO));
    }
}
