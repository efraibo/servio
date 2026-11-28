// 🔵
package app.servio.acesso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * {@code infra/provisionar-gestor.sh --slug=... --email=...}. A senha vem de
 * {@code SERVIO_GESTOR_SENHA}, nunca de argumento, que apareceria na lista de processos.
 */
@Component
@Profile("provisionamento & gestor")
class ComandoProvisionarGestor implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComandoProvisionarGestor.class);

    private final ProvisionamentoDeGestor provisionamento;
    private final Environment ambiente;

    ComandoProvisionarGestor(ProvisionamentoDeGestor provisionamento, Environment ambiente) {
        this.provisionamento = provisionamento;
        this.ambiente = ambiente;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var slug = valor(argumentos, "slug");
        var id = provisionamento.provisionar(
                slug, valor(argumentos, "email"), ambiente.getProperty("SERVIO_GESTOR_SENHA"));
        LOG.info("gestor criado: slug={} id={}", slug, id);
    }

    private static String valor(ApplicationArguments argumentos, String nome) {
        var valores = argumentos.getOptionValues(nome);
        return valores == null || valores.isEmpty() ? null : valores.getFirst();
    }
}
