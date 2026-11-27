// 🔵
package app.servio;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** ADR-0001, condição 13. Hoje não há acesso a dados na produção: os exemplos provam a regra. */
class AcessoTransacionalTest {

    @Test
    void producaoSoAcessaDadosEmTransacao() {
        var producao = new ClassFileImporter().importPath(Path.of("target/classes"));

        assertThat(AcessoTransacional.violacoes(producao)).isEmpty();
    }

    @Test
    void regraAcusaOQueEstaErradoEAceitaOQueEstaCerto() {
        var exemplos = new ClassFileImporter().importClasses(
                SemTransacao.class, LeituraSemReadOnly.class, Correto.class, ClasseAnotada.class, Linhas.class,
                Escapes.class);

        assertThat(AcessoTransacional.violacoes(exemplos))
                .extracting(v -> v.substring(v.indexOf('$') + 1))
                .containsExactlyInAnyOrder(
                        "SemTransacao.porEntityManager: fora de @Transactional",
                        "SemTransacao.porJdbc: fora de @Transactional",
                        "SemTransacao.porRepositorio: fora de @Transactional",
                        "SemTransacao.dentroDeLambda: fora de @Transactional",
                        "LeituraSemReadOnly.ler: leitura sem readOnly = true",
                        "LeituraSemReadOnly.consultar: leitura sem readOnly = true",
                        "Escapes.<init>: acesso onde o proxy não abre transação",
                        "Escapes.privado: acesso onde o proxy não abre transação",
                        "Escapes.chamaPorDentro: chama método transacional da própria classe",
                        "Escapes.semGarantia: fora de @Transactional",
                        "Escapes.jdbcPuro: fora de @Transactional");
    }

    interface Linhas extends CrudRepository<Object, Long> {}

    static class SemTransacao {
        EntityManager entidades;
        JdbcTemplate jdbc;
        Linhas linhas;

        void porEntityManager() {
            entidades.persist(new Object());
        }

        void porJdbc() {
            jdbc.update("delete from x");
        }

        void porRepositorio() {
            linhas.count();
        }

        void dentroDeLambda() {
            List.of(1).forEach(i -> jdbc.update("delete from x"));
        }
    }

    static class LeituraSemReadOnly {
        EntityManager entidades;
        Linhas linhas;

        @Transactional
        Object ler() {
            return linhas.findById(1L);
        }

        @Transactional
        Object consultar() {
            return entidades.createQuery("select 1").getResultList();
        }
    }

    /** Formas de acessar dados sem transação que a anotação sozinha esconderia. */
    static class Escapes {
        JdbcTemplate jdbc;
        DataSource origem;

        Escapes(JdbcTemplate jdbc) {
            jdbc.update("delete from x");
        }

        @Transactional
        private void privado() {
            jdbc.update("delete from x");
        }

        void chamaPorDentro() {
            gravar();
        }

        @Transactional
        void gravar() {
            jdbc.update("delete from x");
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void semGarantia() {
            jdbc.update("delete from x");
        }

        Object jdbcPuro() throws SQLException {
            return origem.getConnection();
        }
    }

    static class Correto {
        EntityManager entidades;
        JdbcTemplate jdbc;
        Linhas linhas;

        @Transactional(readOnly = true)
        Object ler() {
            return linhas.findAll();
        }

        @Transactional
        void gravar() {
            entidades.persist(new Object());
        }

        @Transactional
        void lerEAlterar() {
            linhas.findById(1L);
            entidades.createQuery("delete from X").executeUpdate();
        }

        void semDados() {}
    }

    @Transactional(readOnly = true)
    static class ClasseAnotada {
        JdbcTemplate jdbc;

        Integer contar() {
            return jdbc.queryForObject("select 1", Integer.class);
        }

        @Transactional
        void apagar() {
            jdbc.update("delete from x");
        }
    }
}
