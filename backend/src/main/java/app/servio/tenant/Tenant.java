// 🔵
package app.servio.tenant;

import java.util.UUID;

/** Dados públicos de uma oficina. */
public record Tenant(UUID id, String nome, String slug, String corPrimaria, String corSecundaria, boolean ativo) {}
