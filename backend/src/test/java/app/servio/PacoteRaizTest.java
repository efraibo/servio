package app.servio;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PacoteRaizTest {

    @Test
    void classeDeProducaoFicaDentroDeUmModulo() {
        // importa tudo o que foi compilado, inclusive o que estiver fora de app.servio
        var producao = new ClassFileImporter().importPath(Path.of("target/classes"));
        assertThat(producao).isNotEmpty();

        classes()
                .that().doNotHaveFullyQualifiedName(ServioApplication.class.getName())
                .should().resideInAnyPackage(
                        "app.servio.tenant..",
                        "app.servio.acesso..",
                        "app.servio.cadastros..",
                        "app.servio.agenda..",
                        "app.servio.os..",
                        "app.servio.painel..")
                .allowEmptyShould(true)
                .check(producao);
    }
}
