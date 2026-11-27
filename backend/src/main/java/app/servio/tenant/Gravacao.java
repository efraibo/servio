// 🔵
package app.servio.tenant;

/**
 * Com o RLS, alterar ou apagar sem tenant não dá erro: afeta zero linhas (ADR-0002, regra 15).
 * Envolva nisto toda gravação que precisa ter efeito.
 */
public final class Gravacao {

    private Gravacao() {}

    public static int exigirLinhas(int afetadas) {
        if (afetadas == 0) {
            throw new GravacaoSemEfeitoException(TenantContext.atual().isPresent()
                    ? "a gravação não afetou nenhuma linha"
                    : "a gravação não afetou nenhuma linha: transação sem tenant");
        }
        return afetadas;
    }
}
