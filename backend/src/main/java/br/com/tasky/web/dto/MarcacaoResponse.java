package br.com.tasky.web.dto;

import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.repository.projection.Marcacao;

import java.time.LocalDate;

/** Dia sem marcacao simplesmente nao aparece no historico. */
public record MarcacaoResponse(LocalDate data, StatusRegistroHabito status) {

    public static MarcacaoResponse de(Marcacao marcacao) {
        return new MarcacaoResponse(marcacao.data(), marcacao.status());
    }
}
