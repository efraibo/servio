package app.servio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Única classe de produção fora de {@code app.servio.<módulo>} (SRV-25). */
@SpringBootApplication
public class ServioApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServioApplication.class, args);
    }
}
