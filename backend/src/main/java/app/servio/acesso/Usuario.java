// 🔵
package app.servio.acesso;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Pessoa da equipe de uma oficina e a sua credencial. */
@Entity
@Table(name = "usuarios")
class Usuario {

    @Id
    private UUID id;

    @TenantId
    private UUID tenantId;

    private String email;

    private String senhaHash;

    @Enumerated(EnumType.STRING)
    private Perfil perfil;

    private boolean ativo;

    protected Usuario() {}

    Usuario(String email, String senhaHash, Perfil perfil) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.ativo = true;
    }

    UUID id() {
        return id;
    }
}
