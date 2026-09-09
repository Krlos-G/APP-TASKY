package br.com.tasky.web.dto;

import jakarta.validation.constraints.NotNull;

/** Arquivar e desarquivar pela mesma rota, idempotente nas duas direcoes. */
public record ArquivoRequest(@NotNull(message = "informe o estado") Boolean arquivado) {
}
