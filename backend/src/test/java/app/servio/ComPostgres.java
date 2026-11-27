// 🔵
package app.servio;

import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Um PostgreSQL 18 por execução, com os papéis criados pela mesma pasta de scripts do compose.
 * Sem {@code @ServiceConnection}: ele entregaria o superusuário à aplicação e ao Flyway.
 */
public abstract class ComPostgres {

    private static final String SENHA_OWNER = "ficticia-owner";
    private static final String SENHA_APP = "ficticia-app";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("servio")
            .withEnv("SERVIO_OWNER_PASSWORD", SENHA_OWNER)
            .withEnv("SERVIO_APP_PASSWORD", SENHA_APP)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../infra/postgres/init")),
                    "/docker-entrypoint-initdb.d/");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("SERVIO_DB_URL", POSTGRES::getJdbcUrl);
        registro.add("SERVIO_OWNER_PASSWORD", () -> SENHA_OWNER);
        registro.add("SERVIO_APP_PASSWORD", () -> SENHA_APP);
    }
}
