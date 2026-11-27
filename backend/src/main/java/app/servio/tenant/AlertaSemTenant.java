// 🔵
package app.servio.tenant;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registra um alerta quando uma consulta roda sem {@code app.tenant_id} (ADR-0001, condição 13). Só
 * avisa, não bloqueia: quem protege os dados é o RLS. O log leva nomes de tabela, nunca o SQL.
 */
@Configuration(proxyBeanMethods = false)
class AlertaSemTenant {

    private static final Logger LOG = LoggerFactory.getLogger(AlertaSemTenant.class);

    /** Lista nominal da ADR-0001, condição 12. */
    private static final Set<String> PLATAFORMA = Set.of("tenants", "spring_session", "spring_session_attributes");

    @Bean
    static BeanPostProcessor vigiaConsultasSemTenant() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nome) {
                return bean instanceof DataSource origem ? new Vigiado(origem) : bean;
            }
        };
    }

    private static void conferir(String sql) {
        // o tenant só chega ao banco quando há transação aberta com ele
        if (TenantContext.atual().isPresent() && TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        try {
            var tabelas = TabelasDaConsulta.de(sql);
            tabelas.removeAll(PLATAFORMA);
            if (!tabelas.isEmpty()) {
                LOG.warn("consulta sem app.tenant_id: {}", tabelas);
            }
        } catch (RuntimeException | StackOverflowError e) {
            // o alerta nunca derruba a consulta
            LOG.warn("consulta sem app.tenant_id: não foi possível ler as tabelas ({})", e.getClass().getSimpleName());
        }
    }

    private static final class Vigiado extends DelegatingDataSource {

        Vigiado(DataSource origem) {
            super(origem);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return vigiar(Connection.class, super.getConnection());
        }

        @Override
        public Connection getConnection(String usuario, String senha) throws SQLException {
            return vigiar(Connection.class, super.getConnection(usuario, senha));
        }
    }

    /** Intercepta o SQL entregue à conexão e aos comandos criados por ela. */
    private static <T> T vigiar(Class<T> tipo, T alvo) {
        InvocationHandler chamada = (proxy, metodo, args) -> {
            switch (metodo.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    if (args != null && args.length > 0 && args[0] instanceof String sql
                            && metodo.getName().matches("prepare.*|execute.*|addBatch")) {
                        conferir(sql);
                    }
            }
            try {
                var resultado = metodo.invoke(alvo, args);
                // prepareStatement e prepareCall já foram conferidos ao receber o SQL
                return resultado instanceof Statement comando
                                && !(comando instanceof PreparedStatement || comando instanceof CallableStatement)
                        ? vigiar(Statement.class, comando)
                        : resultado;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[] {tipo}, chamada));
    }
}
