// 🔵
package app.servio.tenant;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Nomes de tabela citados num comando SQL. É uma leitura por padrões, boa para um alerta, e não é uma
 * barreira de segurança. Acusa a mais ({@code extract(... from coluna)}, {@code is distinct from},
 * CTE) e a menos (função que lê tabela, {@code TABLE x}, {@code COPY}, lista depois de subconsulta).
 */
final class TabelasDaConsulta {

    private static final String NOME = "((?:\"[^\"]+\"|\\w+)(?:\\.(?:\"[^\"]+\"|\\w+))?)";
    private static final Pattern MARCA_DE_CIFRAO = Pattern.compile("\\$(?:[A-Za-z_]\\w*)?\\$");
    private static final Pattern CITACAO = Pattern.compile(
            "\\b(from|join|into|update|truncate(?:\\s+table)?)\\s+(?:only\\s+)?" + NOME, Pattern.CASE_INSENSITIVE);
    private static final Pattern PROXIMA_DA_LISTA = Pattern.compile(
            "\\G(?:\\s+(?:as\\s+)?\\w+)?\\s*,\\s*" + NOME, Pattern.CASE_INSENSITIVE);
    private static final Set<String> NAO_SAO_TABELAS = Set.of("of", "set", "skip", "nowait", "select", "lateral");

    private TabelasDaConsulta() {}

    static Set<String> de(String sql) {
        var limpo = semTextoLivre(sql);
        var tabelas = new LinkedHashSet<String>();
        var citacao = CITACAO.matcher(limpo);
        while (citacao.find()) {
            var palavra = citacao.group(1).toLowerCase(Locale.ROOT);
            var funcao = (palavra.equals("from") || palavra.equals("join"))
                    && limpo.substring(citacao.end()).stripLeading().startsWith("(");
            if (!funcao) {
                acrescentar(tabelas, citacao.group(2));
            }
            var proxima = PROXIMA_DA_LISTA.matcher(limpo).region(citacao.end(), limpo.length());
            while (palavra.equals("from") && proxima.find()) {
                if (!limpo.substring(proxima.end()).stripLeading().startsWith("(")) {
                    acrescentar(tabelas, proxima.group(1));
                }
            }
        }
        return tabelas;
    }

    /**
     * Troca literais e comentários por espaço, para que nenhum dado vá ao log. Varre caractere a
     * caractere: uma expressão regular estoura a pilha com literais longos. Texto sem fechamento é
     * descartado até o fim.
     */
    private static String semTextoLivre(String sql) {
        var limpo = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            var cifrao = c == '$' ? MARCA_DE_CIFRAO.matcher(sql).region(i, sql.length()) : null;
            if (c == '\'') {
                // E'...' aceita barra invertida como escape
                i = fimDoLiteral(sql, i, i > 0 && Character.toLowerCase(sql.charAt(i - 1)) == 'e');
            } else if (sql.startsWith("--", i)) {
                var fim = sql.indexOf('\n', i);
                i = fim < 0 ? sql.length() : fim;
            } else if (sql.startsWith("/*", i)) {
                i = fimDoComentario(sql, i);
            } else if (cifrao != null && cifrao.lookingAt()) {
                var fim = sql.indexOf(cifrao.group(), cifrao.end());
                i = fim < 0 ? sql.length() : fim + cifrao.group().length();
            } else {
                limpo.append(c);
                i++;
                continue;
            }
            limpo.append(' ');
        }
        return limpo.toString();
    }

    private static int fimDoLiteral(String sql, int inicio, boolean comBarra) {
        int i = inicio + 1;
        while (i < sql.length()) {
            if (comBarra && sql.charAt(i) == '\\') {
                i += 2;
            } else if (sql.charAt(i) != '\'') {
                i++;
            } else if (sql.startsWith("''", i)) {
                i += 2;
            } else {
                return i + 1;
            }
        }
        return sql.length();
    }

    /** Comentários de bloco podem ser aninhados no PostgreSQL. */
    private static int fimDoComentario(String sql, int inicio) {
        int nivel = 0;
        int i = inicio;
        while (i < sql.length()) {
            if (sql.startsWith("/*", i)) {
                nivel++;
                i += 2;
            } else if (sql.startsWith("*/", i)) {
                i += 2;
                if (--nivel == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return sql.length();
    }

    private static void acrescentar(Set<String> tabelas, String citado) {
        var nome = citado.replace("\"", "").toLowerCase(Locale.ROOT).replaceFirst("^public\\.", "");
        if (!NAO_SAO_TABELAS.contains(nome) && !nome.startsWith("pg_") && !nome.startsWith("information_schema.")) {
            tabelas.add(nome);
        }
    }
}
