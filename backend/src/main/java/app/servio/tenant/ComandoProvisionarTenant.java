// 🔵
package app.servio.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code infra/provisionar.sh --nome=... --slug=... --cor-primaria=#RRGGBB --cor-secundaria=#RRGGBB}.
 * Uma recusa encerra o processo com erro e o motivo.
 */
@Component
@Profile("provisionamento & !gestor")
class ComandoProvisionarTenant implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComandoProvisionarTenant.class);

    private final ProvisionamentoDeTenant provisionamento;

    ComandoProvisionarTenant(ProvisionamentoDeTenant provisionamento) {
        this.provisionamento = provisionamento;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var slug = valor(argumentos, "slug");
        var id = provisionamento.provisionar(
                valor(argumentos, "nome"),
                slug,
                valor(argumentos, "cor-primaria"),
                valor(argumentos, "cor-secundaria"));
        LOG.info("oficina criada: slug={} id={}", slug, id);
    }

    private static String valor(ApplicationArguments argumentos, String nome) {
        var valores = argumentos.getOptionValues(nome);
        return valores == null || valores.isEmpty() ? null : valores.getFirst();
    }
}
