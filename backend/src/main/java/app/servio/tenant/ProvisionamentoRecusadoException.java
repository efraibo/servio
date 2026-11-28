// 🔵
package app.servio.tenant;

public class ProvisionamentoRecusadoException extends RuntimeException {

    public ProvisionamentoRecusadoException(String motivo) {
        super(motivo);
    }
}
