// 🔵
package app.servio.acesso;

import app.servio.tenant.ProvisionamentoRecusadoException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A transação do provisionamento: confere e grava dentro do tenant já definido. */
@Component
class CadastroDoPrimeiroGestor {

    private final Usuarios usuarios;

    private final JdbcTemplate jdbc;

    CadastroDoPrimeiroGestor(Usuarios usuarios, JdbcTemplate jdbc) {
        this.usuarios = usuarios;
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID cadastrar(UUID oficina, String email, String senhaHash) {
        // trava a oficina: duas execuções simultâneas não podem ambas passar pela conferência (RN-23)
        jdbc.queryForList("select id from tenants where id = ? for update", oficina);
        if (usuarios.existsByPerfilAndAtivoTrue(Perfil.GESTOR)) {
            throw new ProvisionamentoRecusadoException("a oficina já tem um Gestor ativo");
        }
        if (usuarios.existsByEmail(email)) {
            throw new ProvisionamentoRecusadoException("o e-mail já existe na oficina");
        }
        return usuarios.save(new Usuario(email, senhaHash, Perfil.GESTOR)).id();
    }
}
