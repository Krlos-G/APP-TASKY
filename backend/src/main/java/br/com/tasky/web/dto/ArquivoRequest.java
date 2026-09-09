package br.com.tasky.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Arquivar e desarquivar pela mesma rota.
 *
 * O estado vai no corpo em vez de virar duas rotas porque o cliente sabe para
 * qual estado quer ir; repetir a chamada com o mesmo valor nao muda nada.
 */
public record ArquivoRequest(@NotNull(message = "informe o estado") Boolean arquivado) {
}
