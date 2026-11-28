// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** ADR-0001, condição 8: entidade sobre tabela com {@code tenant_id} sem {@code @TenantId} quebra o build. */
@SpringBootTest
class TenantIdObrigatorioTest extends ComPostgres {

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void todasAsEntidadesComTenantIdTemAAnotacao() {
        var entidades = entidades();

        assertThat(entidades.keySet()).extracting(Class::getSimpleName).contains("Usuario");
        assertThat(TenantIdObrigatorio.violacoes(entidades, tabelasComTenantId())).isEmpty();
    }

    @Test
    void acusaEntidadeSemAAnotacao() {
        class SemAnotacao {
            @SuppressWarnings("unused")
            UUID tenantId;
        }

        assertThat(TenantIdObrigatorio.violacoes(Map.of(SemAnotacao.class, "usuarios"), tabelasComTenantId()))
                .singleElement()
                .asString()
                .contains("SemAnotacao", "@TenantId");
    }

    @Test
    void acusaTambemQuandoATabelaVemComSchemaAspasOuMaiusculas() {
        class SemAnotacao {}

        for (var tabela : List.of("public.usuarios", "\"usuarios\"", "PUBLIC.Usuarios")) {
            assertThat(TenantIdObrigatorio.violacoes(Map.of(SemAnotacao.class, tabela), tabelasComTenantId()))
                    .as(tabela)
                    .hasSize(1);
        }
    }

    /** Classe de cada entidade que o Hibernate conhece e a tabela dela. */
    private Map<Class<?>, String> entidades() {
        var entidades = new HashMap<Class<?>, String>();
        fabrica.unwrap(SessionFactoryImplementor.class)
                .getMappingMetamodel()
                .forEachEntityDescriptor(d -> entidades.put(
                        d.getMappedClass(), ((AbstractEntityPersister) d).getTableName()));
        return entidades;
    }

    private Set<String> tabelasComTenantId() {
        List<String> tabelas = jdbc.queryForList("""
                select table_name::text from information_schema.columns
                where table_schema = 'public' and column_name = 'tenant_id'""", String.class);
        return Set.copyOf(tabelas);
    }
}
