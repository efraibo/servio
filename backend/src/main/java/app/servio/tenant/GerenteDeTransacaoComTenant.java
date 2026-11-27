// 🔵
package app.servio.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import org.hibernate.Session;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;

/**
 * Único ponto que define {@code app.tenant_id}. Roda ao abrir toda transação física, inclusive
 * {@code REQUIRES_NEW}; o valor é local à transação. Sem tenant, nada é definido e o RLS fecha.
 * JPA e {@code JdbcTemplate} usam a mesma conexão desta transação.
 *
 * <p>Fica dentro da abertura da transação para que uma falha aqui devolva a conexão. Um gerente de
 * transação criado à parte não passa por aqui.
 */
@Component("transactionManager")
class GerenteDeTransacaoComTenant extends JpaTransactionManager {

    GerenteDeTransacaoComTenant(EntityManagerFactory fabrica) {
        super(fabrica);
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        // depois do super, que instala o dialeto da fábrica por cima de qualquer outro
        setJpaDialect(new DialetoComTenant());
    }

    private static final class DialetoComTenant extends HibernateJpaDialect {

        @Override
        public Object beginTransaction(EntityManager entidades, TransactionDefinition definicao)
                throws PersistenceException, SQLException, TransactionException {
            var dados = super.beginTransaction(entidades, definicao);
            var tenant = TenantContext.atual();
            if (tenant.isPresent()) {
                entidades.unwrap(Session.class).doWork(conexao -> {
                    try (var sql = conexao.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                        sql.setString(1, tenant.get().toString());
                        sql.execute();
                    }
                });
            }
            return dados;
        }
    }
}
