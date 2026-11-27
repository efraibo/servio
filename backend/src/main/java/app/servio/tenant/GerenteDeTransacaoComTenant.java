// 🔵
package app.servio.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;

/**
 * Único ponto que define {@code app.tenant_id}. Roda ao abrir toda transação física, inclusive
 * {@code REQUIRES_NEW}; o valor é local à transação. Sem tenant, nada é definido e o RLS fecha.
 *
 * <p>Fica dentro da abertura da transação para que uma falha aqui devolva a conexão. Um gerente de
 * transação criado à parte não passa por aqui. Com JPA (SRV-43), o gancho precisa ser revisto.
 */
@Component("transactionManager")
class GerenteDeTransacaoComTenant extends JdbcTransactionManager {

    GerenteDeTransacaoComTenant(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void prepareTransactionalConnection(Connection conexao, TransactionDefinition definicao)
            throws SQLException {
        super.prepareTransactionalConnection(conexao, definicao);
        var tenant = TenantContext.atual();
        if (tenant.isPresent()) {
            try (var sql = conexao.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                sql.setString(1, tenant.get().toString());
                sql.execute();
            }
        }
    }
}
