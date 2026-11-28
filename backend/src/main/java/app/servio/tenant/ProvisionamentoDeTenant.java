// 🔵
package app.servio.tenant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria e consulta oficinas (RN-17, RN-18, RN-24). Criar exige o papel dono: no papel da aplicação o
 * banco recusa.
 */
@Service
public class ProvisionamentoDeTenant {

    // ao menos uma letra
    private static final Pattern SLUG = Pattern.compile("[a-z0-9-]*[a-z][a-z0-9-]*");
    private static final int TAMANHO_MAXIMO_DO_SLUG = 63;
    // primeiro segmento das rotas da API: /api/{slug}/login
    private static final String SLUG_RESERVADO = "api";
    private static final Pattern COR = Pattern.compile("#[0-9A-Fa-f]{6}");

    private static final String SELECAO = "select id, nome, slug, cor_primaria, cor_secundaria, ativo from tenants";

    private final JdbcTemplate jdbc;

    ProvisionamentoDeTenant(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID provisionar(String nome, String slug, String corPrimaria, String corSecundaria) {
        exigir(nome != null && !nome.isBlank(), "o nome é obrigatório");
        // o tamanho vem antes do regex, que é quadrático em entradas enormes
        exigir(slug == null || slug.length() <= TAMANHO_MAXIMO_DO_SLUG,
                "slug inválido: no máximo " + TAMANHO_MAXIMO_DO_SLUG + " caracteres");
        exigir(slug != null && SLUG.matcher(slug).matches(),
                "slug inválido: use letras minúsculas sem acento, números e hífen, com ao menos uma letra");
        exigir(!slug.equals(SLUG_RESERVADO), "slug inválido: " + SLUG_RESERVADO + " é reservado");
        exigir(corPrimaria != null && COR.matcher(corPrimaria).matches(), "cor primária inválida: use #RRGGBB");
        exigir(corSecundaria != null && COR.matcher(corSecundaria).matches(), "cor secundária inválida: use #RRGGBB");

        var id = UUID.randomUUID();
        try {
            jdbc.update(
                    "insert into tenants (id, nome, slug, cor_primaria, cor_secundaria) values (?, ?, ?, ?, ?)",
                    id, nome.strip(), slug, corPrimaria, corSecundaria);
        } catch (DuplicateKeyException e) {
            throw new ProvisionamentoRecusadoException("slug já usado por outra oficina: " + slug);
        }
        return id;
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> consultar(UUID id) {
        return jdbc.query(SELECAO + " where id = ?", ProvisionamentoDeTenant::tenant, id).stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> consultarPorSlug(String slug) {
        return jdbc.query(SELECAO + " where slug = ?", ProvisionamentoDeTenant::tenant, slug).stream()
                .findFirst();
    }

    private static Tenant tenant(ResultSet linha, int numero) throws SQLException {
        return new Tenant(
                linha.getObject("id", UUID.class),
                linha.getString("nome"),
                linha.getString("slug"),
                linha.getString("cor_primaria"),
                linha.getString("cor_secundaria"),
                linha.getBoolean("ativo"));
    }

    private static void exigir(boolean condicao, String motivo) {
        if (!condicao) {
            throw new ProvisionamentoRecusadoException(motivo);
        }
    }
}
