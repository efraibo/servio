// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigracoesTest {

    @Test
    void migracoesSaoSqlPuroEmDbMigration() throws IOException {
        try (var arquivos = Files.walk(Path.of("src/main/resources/db/migration"))) {
            assertThat(arquivos.filter(Files::isRegularFile).map(a -> a.getFileName().toString()))
                    .isNotEmpty()
                    .allMatch(nome -> nome.matches("V\\d+__[a-z0-9_]+\\.sql"));
        }
        assertThat(Path.of("src/main/java/db")).doesNotExist();
    }
}
