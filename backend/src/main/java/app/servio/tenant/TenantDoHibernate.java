// 🔵
package app.servio.tenant;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/** Entrega ao Hibernate o mesmo tenant que o ponto único leva ao banco (ADR-0001, condição 8). */
@Component
class TenantDoHibernate implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        // o Hibernate exige um valor: o UUID zero não casa com linha e o RLS recusa gravá-lo
        return TenantContext.atual().orElse(TenantContext.NENHUM);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
