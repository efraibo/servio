// 🔵
package app.servio.acesso;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Hash das senhas (ADR-0002, regra 1). Argon2 não limita o tamanho da senha, como o BCrypt limita. */
@Configuration(proxyBeanMethods = false)
class Senhas {

    @Bean
    PasswordEncoder codificadorDeSenhas() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
