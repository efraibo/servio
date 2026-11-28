package app.servio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Única classe de produção fora de {@code app.servio.<módulo>} (SRV-25). */
@SpringBootApplication
public class ServioApplication {

    public static void main(String[] args) {
        var contexto = SpringApplication.run(ServioApplication.class, args);
        // o provisionamento é um comando: termina em vez de ficar no ar
        if (contexto.getEnvironment().matchesProfiles("provisionamento")) {
            System.exit(SpringApplication.exit(contexto));
        }
    }
}
