// 🔵
package app.servio.acesso;

import app.servio.tenant.ProvisionamentoDeTenant;
import app.servio.tenant.ProvisionamentoRecusadoException;
import app.servio.tenant.Tenant;
import app.servio.tenant.TenantContext;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Cria o primeiro Gestor de uma oficina (RN-18, RN-19, RN-22, RN-23). Nunca altera um usuário que já
 * existe. Sem {@code @Transactional} aqui: o tenant precisa estar definido antes de a transação abrir.
 */
@Service
public class ProvisionamentoDeGestor {

    private static final int TAMANHO_MINIMO_DA_SENHA = 8;
    private static final int TAMANHO_MAXIMO_DO_EMAIL = 254;

    // "espaço" é qualquer espaço em branco Unicode, inclusive tab e espaço não separável
    private static final Pattern SO_ESPACOS = Pattern.compile("\\s*", Pattern.UNICODE_CHARACTER_CLASS);
    // formato básico (RN-22): um "@", algo antes e depois, nenhum espaço
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+", Pattern.UNICODE_CHARACTER_CLASS);

    private final ProvisionamentoDeTenant oficinas;
    private final CadastroDoPrimeiroGestor cadastro;
    private final PasswordEncoder codificador;

    ProvisionamentoDeGestor(
            ProvisionamentoDeTenant oficinas, CadastroDoPrimeiroGestor cadastro, PasswordEncoder codificador) {
        this.oficinas = oficinas;
        this.cadastro = cadastro;
        this.codificador = codificador;
    }

    public UUID provisionar(String slug, String email, String senha) {
        exigir(email != null && !email.isBlank(), "o e-mail é obrigatório");
        exigir(senha != null && senha.codePointCount(0, senha.length()) >= TAMANHO_MINIMO_DA_SENHA,
                "senha com no mínimo " + TAMANHO_MINIMO_DA_SENHA + " caracteres");
        exigir(!SO_ESPACOS.matcher(senha).matches(), "a senha não pode ser formada só por espaços");
        // valida o que será gravado: sem os espaços das pontas e em minúsculas
        var normalizado = email.strip().toLowerCase(Locale.ROOT);
        exigir(EMAIL.matcher(normalizado).matches(), "e-mail inválido: use o formato nome@dominio, sem espaços");
        exigir(normalizado.codePointCount(0, normalizado.length()) <= TAMANHO_MAXIMO_DO_EMAIL,
                "e-mail com no máximo " + TAMANHO_MAXIMO_DO_EMAIL + " caracteres");
        var oficina = oficinas.consultarPorSlug(slug)
                .map(Tenant::id)
                .orElseThrow(() -> new ProvisionamentoRecusadoException("oficina inexistente: " + slug));

        var hash = codificador.encode(senha);
        return TenantContext.executarComo(oficina, () -> cadastro.cadastrar(oficina, normalizado, hash));
    }

    private static void exigir(boolean condicao, String motivo) {
        if (!condicao) {
            throw new ProvisionamentoRecusadoException(motivo);
        }
    }
}
