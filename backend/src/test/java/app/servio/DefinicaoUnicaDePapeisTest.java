// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** ADR-0003: {@code infra/postgres/init/} é a única definição dos papéis. */
class DefinicaoUnicaDePapeisTest {

    // entre as palavras aceita espaço ou comentário de bloco
    private static final Pattern DEFINE_PAPEL = Pattern.compile(
            "\\bcreate(\\s|/\\*.*?\\*/)+(role|user|group)\\b|\\balter(\\s|/\\*.*?\\*/)+(role|user|group)\\b|\\bcreateuser\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Set<String> IGNORADAS = Set.of(".git", "target", "node_modules");
    private static final Set<String> IGNORADAS_SO_NA_RAIZ = Set.of("docs", ".claude");
    private static final Set<String> EXTENSOES = Set.of(
            "sql", "psql", "ddl", "sh", "java", "kt", "yaml", "yml", "properties", "xml", "json", "ts", "js", "env",
            "Dockerfile");

    @Test
    void papeisSaoDefinidosSoEmInfraPostgresInit() throws IOException {
        var raiz = Path.of("..").toRealPath();
        var definicoes = new ArrayList<Path>();
        Files.walkFileTree(raiz, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                var nome = String.valueOf(dir.getFileName());
                var naRaiz = raiz.equals(dir.getParent());
                return IGNORADAS.contains(nome) || (naRaiz && IGNORADAS_SO_NA_RAIZ.contains(nome))
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path arquivo, BasicFileAttributes attrs) {
                var nome = arquivo.getFileName().toString();
                if (EXTENSOES.contains(nome.substring(nome.lastIndexOf('.') + 1)) && define(arquivo)) {
                    definicoes.add(raiz.relativize(arquivo));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertThat(definicoes).containsExactlyElementsOf(List.of(Path.of("infra/postgres/init/01-roles.sql")));
    }

    private static boolean define(Path arquivo) {
        try {
            return DEFINE_PAPEL.matcher(new String(Files.readAllBytes(arquivo), StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
