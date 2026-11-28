// 🔵
package app.servio;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.hibernate.annotations.TenantId;

/** Lista as entidades sobre tabela com {@code tenant_id} que não têm {@code @TenantId}. */
final class TenantIdObrigatorio {

    private TenantIdObrigatorio() {}

    static List<String> violacoes(Map<Class<?>, String> entidades, Set<String> tabelasComTenantId) {
        return entidades.entrySet().stream()
                .filter(e -> tabelasComTenantId.contains(nomeSimples(e.getValue())) && !temTenantId(e.getKey()))
                .map(e -> e.getKey().getName() + ": tabela " + e.getValue() + " tem tenant_id e falta @TenantId")
                .sorted()
                .toList();
    }

    /** Sem schema, aspas ou caixa, como o information_schema guarda os nomes não citados. */
    private static String nomeSimples(String tabela) {
        var nome = tabela.substring(tabela.lastIndexOf('.') + 1);
        return nome.replace("\"", "").replace("`", "").toLowerCase(Locale.ROOT);
    }

    private static boolean temTenantId(Class<?> classe) {
        for (var atual = classe; atual != null; atual = atual.getSuperclass()) {
            if (Arrays.stream(atual.getDeclaredFields()).anyMatch(f -> f.isAnnotationPresent(TenantId.class))) {
                return true;
            }
        }
        return false;
    }
}
