// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.servio.tenant.ProvisionamentoDeTenant;
import app.servio.tenant.ProvisionamentoRecusadoException;
import app.servio.tenant.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** SRV-6, cenários 1 a 4 (RN-17, RN-18, RN-24). */
@SpringBootTest(args = {"--nome=Inicial", "--slug=inicial", "--cor-primaria=#000000", "--cor-secundaria=#FFFFFF"})
@ActiveProfiles("provisionamento")
class ProvisionamentoDeTenantTest extends ComPostgres {

    @Autowired
    private ProvisionamentoDeTenant provisionamento;

    @Autowired
    private ApplicationRunner comando;

    @Autowired
    private JdbcTemplate jdbc;

    // antes também: o contexto sobe com argumentos e já roda o comando uma vez
    @BeforeEach
    @AfterEach
    void limpa() {
        jdbc.update("delete from tenants");
    }

    @Test
    void criaTenantAtivoComMarcaEConsultaDevolve() {
        var id = provisionamento.provisionar("Oficina A", "oficina-a", "#1A73E8", "#F4B400");

        assertThat(provisionamento.consultar(id))
                .contains(new Tenant(id, "Oficina A", "oficina-a", "#1A73E8", "#F4B400", true));
    }

    @Test
    void oComandoLeOsArgumentos() throws Exception {
        comando.run(new DefaultApplicationArguments(
                "--nome=Moto do Zé", "--slug=moto-ze", "--cor-primaria=#112233", "--cor-secundaria=#445566"));

        assertThat(jdbc.queryForObject("select nome from tenants where slug = 'moto-ze'", String.class))
                .isEqualTo("Moto do Zé");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULO", textBlock = """
            '',       oficina-a, '#1A73E8', '#F4B400', nome é obrigatório
            '   ',    oficina-a, '#1A73E8', '#F4B400', nome é obrigatório
            NULO,     oficina-a, '#1A73E8', '#F4B400', nome é obrigatório
            Oficina,  oficina-a, azul,      '#F4B400', cor primária
            Oficina,  oficina-a, '#12345',  '#F4B400', cor primária
            Oficina,  oficina-a, NULO,      '#F4B400', cor primária
            Oficina,  oficina-a, '#1A73E8', '#GGGGGG', cor secundária
            Oficina,  oficina-a, '#1A73E8', NULO,      cor secundária
            Oficina,  Oficina-A, '#1A73E8', '#F4B400', slug
            Oficina,  oficina á, '#1A73E8', '#F4B400', slug
            Oficina,  oficina_a, '#1A73E8', '#F4B400', slug
            Oficina,  '',        '#1A73E8', '#F4B400', slug
            Oficina,  NULO,      '#1A73E8', '#F4B400', slug
            """)
    void entradaInvalidaNaoCriaEDizOMotivo(
            String nome, String slug, String primaria, String secundaria, String motivo) {
        assertThatThrownBy(() -> provisionamento.provisionar(nome, slug, primaria, secundaria))
                .isInstanceOf(ProvisionamentoRecusadoException.class)
                .hasMessageContaining(motivo);
        assertThat(jdbc.queryForObject("select count(*) from tenants", Integer.class)).isZero();
    }

    @Test
    void slugRepetidoNaoCria() {
        provisionamento.provisionar("Oficina A", "oficina-a", "#1A73E8", "#F4B400");

        assertThatThrownBy(() -> provisionamento.provisionar("Outra", "oficina-a", "#000000", "#FFFFFF"))
                .isInstanceOf(ProvisionamentoRecusadoException.class)
                .hasMessageContaining("slug já usado");
        assertThat(jdbc.queryForList("select nome from tenants", String.class)).containsExactly("Oficina A");
    }
}
