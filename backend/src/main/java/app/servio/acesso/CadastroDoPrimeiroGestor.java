// 🔵
package app.servio.acesso;

import app.servio.tenant.ProvisionamentoRecusadoException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A transação do provisionamento: confere e grava dentro do tenant já definido. */
@Component
class CadastroDoPrimeiroGestor {

    private final Usuarios usuarios;

    CadastroDoPrimeiroGestor(Usuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional
    public UUID cadastrar(String email, String senhaHash) {
        if (usuarios.existsByPerfilAndAtivoTrue(Perfil.GESTOR)) {
            throw new ProvisionamentoRecusadoException("a oficina já tem um Gestor ativo");
        }
        if (usuarios.existsByEmail(email)) {
            throw new ProvisionamentoRecusadoException("o e-mail já existe na oficina: " + email);
        }
        return usuarios.save(new Usuario(email, senhaHash, Perfil.GESTOR)).id();
    }
}
