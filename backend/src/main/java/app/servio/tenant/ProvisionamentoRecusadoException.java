// 🔵
package app.servio.tenant;

public class ProvisionamentoRecusadoException extends RuntimeException {

    ProvisionamentoRecusadoException(String motivo) {
        super(motivo);
    }
}
