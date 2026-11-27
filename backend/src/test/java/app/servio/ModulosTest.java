package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.core.ApplicationModules;

class ModulosTest {

    /** Tabela de dependências da ADR-0003, item 3. */
    private static final Map<String, String[]> ADR_0003 = Map.of(
            "tenant", new String[] {},
            "acesso", new String[] {"tenant"},
            "cadastros", new String[] {"tenant"},
            "agenda", new String[] {"cadastros"},
            "os", new String[] {"cadastros", "agenda"},
            "painel", new String[] {"agenda", "os"});

    private final ApplicationModules modulos = ApplicationModules.of(ServioApplication.class);

    @Test
    void existemExatamenteOsSeisModulos() {
        assertThat(modulos.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrderElementsOf(ADR_0003.keySet());
    }

    @Test
    void cadaModuloDeclaraAsDependenciasDaAdr() throws ClassNotFoundException {
        for (var entrada : ADR_0003.entrySet()) {
            var declaracao = Class.forName("app.servio." + entrada.getKey() + ".package-info")
                    .getAnnotation(ApplicationModule.class);
            assertThat(declaracao.allowedDependencies())
                    .as(entrada.getKey())
                    .containsExactlyInAnyOrder(entrada.getValue());
            // módulo OPEN exporia as classes internas e verify() não reclamaria
            assertThat(declaracao.type())
                    .as(entrada.getKey())
                    .isEqualTo(ApplicationModule.Type.CLOSED);
        }
    }

    @Test
    void nenhumModuloEhCompartilhadoForaDaTabela() {
        // sharedModules de @Modulithic libera um módulo para todos os outros
        assertThat(ServioApplication.class.getAnnotation(Modulithic.class)).isNull();
    }

    @Test
    void fronteirasEntreModulosSaoRespeitadas() {
        modulos.verify();
    }
}
