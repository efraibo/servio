// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.servio.acesso.ProvisionamentoDeGestor;
import app.servio.tenant.ProvisionamentoDeTenant;
import app.servio.tenant.ProvisionamentoRecusadoException;
import app.servio.tenant.TenantContext;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SRV-21, cenários 1 a 5 (RN-18, RN-19, RN-22, RN-23). */
@SpringBootTest(
        args = {"--slug=inicial", "--email=inicial@oficina.test"},
        properties = "SERVIO_GESTOR_SENHA=senha-inicial-ficticia")
@ActiveProfiles({"provisionamento", "gestor"})
@ExtendWith(OutputCaptureExtension.class)
class ProvisionamentoDeGestorTest extends ComPostgres {

    private static final String SENHA = "senha-ficticia-1";

    // antes do contexto: o comando já roda uma vez na subida e precisa do esquema e da oficina
    static {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER)
                .load()
                .migrate();
        try {
            limpar();
            comoDono("insert into tenants (id, nome, slug, cor_primaria, cor_secundaria)"
                    + " values (gen_random_uuid(), 'Inicial', 'inicial', '#000000', '#FFFFFF')");
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired
    private ProvisionamentoDeGestor gestores;

    @Autowired
    private ProvisionamentoDeTenant oficinas;

    @Autowired
    private ApplicationRunner comando;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager gerente;

    @Autowired
    private PasswordEncoder codificador;

    private UUID oficina;

    @BeforeEach
    void cria() throws SQLException {
        limpar();
        oficina = oficinas.provisionar("Oficina A", "oficina-a", "#1A73E8", "#F4B400");
    }

    @AfterEach
    void remove() throws SQLException {
        limpar();
    }

    @Test
    void criaGestorAtivoNoTenantComEmailEmMinusculas() {
        var id = gestores.provisionar("oficina-a", "Gestor@Oficina.test", SENHA);

        assertThat(usuarios(oficina)).singleElement().satisfies(u -> assertThat(u)
                .containsEntry("id", id)
                .containsEntry("tenant_id", oficina)
                .containsEntry("email", "gestor@oficina.test")
                .containsEntry("perfil", "GESTOR")
                .containsEntry("ativo", true));
    }

    @Test
    void guardaSoOHashDaSenha(CapturedOutput saida) throws Exception {
        comando.run(new DefaultApplicationArguments("--slug=oficina-a", "--email=gestor@oficina.test"));

        var linha = usuarios(oficina).getFirst();
        var hash = (String) linha.get("senha_hash");
        assertThat(hash).startsWith("$argon2");
        assertThat(codificador.matches("senha-inicial-ficticia", hash)).isTrue();
        assertThat(linha.values()).doesNotContain("senha-inicial-ficticia");
        assertThat(saida.getAll()).contains("gestor criado").doesNotContain("senha-inicial-ficticia");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULO", textBlock = """
            oficina-a,   gestor@oficina.test, 1234567,          senha com no mínimo 8 caracteres
            oficina-a,   gestor@oficina.test, NULO,             senha com no mínimo 8 caracteres
            nao-existe,  gestor@oficina.test, senha-ficticia-1, oficina inexistente
            NULO,        gestor@oficina.test, senha-ficticia-1, oficina inexistente
            oficina-a,   '   ',              senha-ficticia-1, e-mail é obrigatório
            oficina-a,   NULO,               senha-ficticia-1, e-mail é obrigatório
            """)
    void entradaInvalidaNaoCriaEDizOMotivo(String slug, String email, String senha, String motivo) {
        assertThatThrownBy(() -> gestores.provisionar(slug, email, senha))
                .isInstanceOf(ProvisionamentoRecusadoException.class)
                .hasMessageContaining(motivo)
                .satisfies(e -> {
                    if (senha != null) {
                        assertThat(e.getMessage()).doesNotContain(senha);
                    }
                });
        assertThat(usuarios(oficina)).isEmpty();
    }

    @Test
    void emailRepetidoNaOficinaNaoCriaMasValeEmOutra() {
        inserir("gestor@oficina.test", "ATENDENTE", true);

        assertThatThrownBy(() -> gestores.provisionar("oficina-a", "GESTOR@oficina.test", SENHA))
                .isInstanceOf(ProvisionamentoRecusadoException.class)
                .hasMessageContaining("e-mail já existe na oficina")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContainIgnoringCase("@oficina.test"));
        assertThat(usuarios(oficina)).hasSize(1);

        var outra = oficinas.provisionar("Oficina B", "oficina-b", "#000000", "#FFFFFF");
        gestores.provisionar("oficina-b", "GESTOR@oficina.test", SENHA);
        assertThat(usuarios(outra)).hasSize(1);
    }

    @Test
    void comGestorAtivoFalhaSemAlterar() {
        gestores.provisionar("oficina-a", "gestor@oficina.test", SENHA);
        var antes = usuarios(oficina);

        for (var email : List.of("gestor@oficina.test", "outro@oficina.test")) {
            assertThatThrownBy(() -> gestores.provisionar("oficina-a", email, "outra-senha-ficticia"))
                    .isInstanceOf(ProvisionamentoRecusadoException.class)
                    .hasMessageContaining("já tem um Gestor ativo");
        }
        assertThat(usuarios(oficina)).isEqualTo(antes);
    }

    @Test
    void senhaComExatamenteOitoCaracteresEEmailComEspacosSaoAceitos() {
        gestores.provisionar("oficina-a", "  Gestor@Oficina.test ", "12345678");

        assertThat(usuarios(oficina)).singleElement().satisfies(u -> assertThat(u)
                .containsEntry("email", "gestor@oficina.test"));
    }

    @Test
    void gestorInativoNaoImpedeUmNovo() {
        inserir("antigo@oficina.test", "GESTOR", false);

        gestores.provisionar("oficina-a", "gestor@oficina.test", SENHA);

        assertThat(usuarios(oficina)).hasSize(2);
    }

    /** Duas execuções ao mesmo tempo: a segunda espera a primeira e enxerga o Gestor dela (RN-23). */
    @Test
    void execucaoSimultaneaEsperaERecusa() throws Exception {
        try (var outra = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "servio_owner", SENHA_OWNER);
                var sql = outra.createStatement();
                var executor = Executors.newSingleThreadExecutor()) {
            outra.setAutoCommit(false);
            sql.execute("select set_config('app.tenant_id', '" + oficina + "', true)");
            sql.execute("select id from tenants where id = '" + oficina + "' for update");
            sql.execute("insert into usuarios (tenant_id, id, email, senha_hash, perfil) values ('" + oficina
                    + "', gen_random_uuid(), 'primeiro@oficina.test', 'x', 'GESTOR')");

            var concorrente = executor.submit(() -> gestores.provisionar("oficina-a", "segundo@oficina.test", SENHA));
            assertThatThrownBy(() -> concorrente.get(3, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            outra.commit();

            assertThatThrownBy(() -> concorrente.get(20, TimeUnit.SECONDS))
                    .hasMessageContaining("já tem um Gestor ativo");
        }
        assertThat(usuarios(oficina)).hasSize(1);
    }

    private void inserir(String email, String perfil, boolean ativo) {
        TenantContext.executarComo(oficina, () -> new TransactionTemplate(gerente)
                .execute(t -> jdbc.update(
                        "insert into usuarios (tenant_id, id, email, senha_hash, perfil, ativo) values (?, ?, ?, 'x', ?, ?)",
                        oficina, UUID.randomUUID(), email, perfil, ativo)));
    }

    private List<Map<String, Object>> usuarios(UUID tenant) {
        return TenantContext.executarComo(tenant, () -> new TransactionTemplate(gerente)
                .execute(s -> jdbc.queryForList("select * from usuarios")));
    }

    /** TRUNCATE ignora o RLS: é como o teste esvazia as tabelas entre os casos. */
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
