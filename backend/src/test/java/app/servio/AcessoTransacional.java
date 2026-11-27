// 🔵
package app.servio;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.hibernate.StatelessSession;
import org.hibernate.query.CommonQueryContract;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo método que acessa dados está em {@code @Transactional} do Spring (nele ou na classe), e o que só
 * lê usa {@code readOnly = true}. A anotação precisa funcionar com o proxy: método privado, estático,
 * construtor, chamada a partir da própria classe e propagação sem transação não contam. Leitura e
 * escrita são decididas pelo nome do método chamado.
 */
final class AcessoTransacional {

    private static final List<Class<?>> ACESSOS = List.of(
            EntityManager.class,
            Query.class,
            CommonQueryContract.class,
            StatelessSession.class,
            JdbcOperations.class,
            NamedParameterJdbcOperations.class,
            JdbcClient.class,
            JdbcClient.StatementSpec.class,
            SimpleJdbcInsert.class,
            SimpleJdbcCall.class,
            DataSource.class,
            Connection.class,
            Statement.class,
            Repository.class);

    private static final Set<Propagation> SEM_TRANSACAO_GARANTIDA =
            Set.of(Propagation.NOT_SUPPORTED, Propagation.SUPPORTS, Propagation.NEVER);

    private static final Pattern ESCRITA = Pattern.compile(
            "(persist|merge|remove|flush|lock|executeUpdate|update|batchUpdate|execute|save|delete|insert).*");

    /** Infraestrutura que abre a transação ou envolve a conexão: isenta, uma a uma, por nome. */
    private static final Set<String> INFRAESTRUTURA = Set.of(
            "app.servio.tenant.GerenteDeTransacaoComTenant$DialetoComTenant",
            "app.servio.tenant.AlertaSemTenant",
            "app.servio.tenant.AlertaSemTenant$1",
            "app.servio.tenant.AlertaSemTenant$Vigiado");

    private AcessoTransacional() {}

    static List<String> violacoes(JavaClasses classes) {
        var violacoes = new ArrayList<String>();
        for (var classe : classes) {
            if (INFRAESTRUTURA.contains(classe.getName())) {
                continue;
            }
            // inclui construtores e inicializadores estáticos
            for (var codigo : classe.getCodeUnits()) {
                if (!codigo.getModifiers().contains(JavaModifier.SYNTHETIC)) {
                    conferir(codigo, violacoes);
                    conferirAutoChamada(codigo, violacoes);
                }
            }
        }
        return violacoes;
    }

    private static void conferir(JavaCodeUnit codigo, List<String> violacoes) {
        // chamadas e referências (jdbc::update); o ArchUnit atribui as de lambdas a quem as contém
        var dados = Stream.<JavaAccess<?>>concat(
                        codigo.getMethodCallsFromSelf().stream(), codigo.getMethodReferencesFromSelf().stream())
                .filter(acesso -> ACESSOS.stream().anyMatch(tipo -> acesso.getTargetOwner().isAssignableTo(tipo)))
                .map(acesso -> acesso.getName())
                .toList();
        if (dados.isEmpty()) {
            return;
        }
        var nome = nome(codigo);
        if (!(codigo instanceof JavaMethod metodo)
                || metodo.getModifiers().contains(JavaModifier.PRIVATE)
                || metodo.getModifiers().contains(JavaModifier.STATIC)) {
            violacoes.add(nome + ": acesso onde o proxy não abre transação");
            return;
        }
        var transacao = transacao(metodo);
        if (transacao.isEmpty() || SEM_TRANSACAO_GARANTIDA.contains(transacao.get().propagation())) {
            violacoes.add(nome + ": fora de @Transactional");
        } else if (!transacao.get().readOnly() && dados.stream().noneMatch(d -> ESCRITA.matcher(d).matches())) {
            violacoes.add(nome + ": leitura sem readOnly = true");
        }
    }

    /** Chamar um método anotado da própria classe passa por fora do proxy. */
    private static void conferirAutoChamada(JavaCodeUnit codigo, List<String> violacoes) {
        if (codigo instanceof JavaMethod metodo && transacao(metodo).isPresent()) {
            return;
        }
        var passaPorFora = codigo.getMethodCallsFromSelf().stream()
                .filter(chamada -> chamada.getTargetOwner().equals(codigo.getOwner()))
                .anyMatch(chamada -> chamada.getTarget().resolveMember()
                        .map(alvo -> alvo instanceof JavaMethod m && transacao(m).isPresent())
                        .orElse(false));
        if (passaPorFora) {
            violacoes.add(nome(codigo) + ": chama método transacional da própria classe");
        }
    }

    private static Optional<Transactional> transacao(JavaMethod metodo) {
        return metodo.tryGetAnnotationOfType(Transactional.class)
                .or(() -> metodo.getOwner().tryGetAnnotationOfType(Transactional.class));
    }

    private static String nome(JavaCodeUnit codigo) {
        return codigo.getOwner().getName() + "." + codigo.getName();
    }
}
