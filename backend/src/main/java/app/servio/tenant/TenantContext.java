// 🔵
package app.servio.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tenant do trecho de código em execução. Vale só dentro de {@link #executarComo} e é levado ao banco
 * no início de cada transação (ADR-0001, condição 3).
 */
public final class TenantContext {

    /** Reservado: é o que o Hibernate recebe quando não há tenant. */
    static final UUID NENHUM = new UUID(0, 0);

    private static final ScopedValue<UUID> ATUAL = ScopedValue.newInstance();

    private TenantContext() {}

    public static <T> T executarComo(UUID tenant, Supplier<T> acao) {
        Objects.requireNonNull(tenant, "tenant");
        if (tenant.equals(NENHUM)) {
            throw new IllegalArgumentException("o UUID zero significa ausência de tenant");
        }
        // app.tenant_id é definido no início da transação: trocar no meio não teria efeito no banco
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && !atual().filter(tenant::equals).isPresent()) {
            throw new IllegalStateException("o tenant não pode ser trocado dentro de uma transação");
        }
        return ScopedValue.where(ATUAL, tenant).call(acao::get);
    }

    public static Optional<UUID> atual() {
        return ATUAL.isBound() ? Optional.of(ATUAL.get()) : Optional.empty();
    }
}
