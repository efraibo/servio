// 🔵
package app.servio.acesso;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sempre dentro de uma transação com tenant: o Hibernate e o RLS filtram por ele. */
interface Usuarios extends JpaRepository<Usuario, UUID> {

    boolean existsByPerfilAndAtivoTrue(Perfil perfil);

    boolean existsByEmail(String email);
}
