package br.com.tasky.service;

import br.com.tasky.web.ApiException;

import java.time.DateTimeException;
import java.time.ZoneId;

/** Uma validacao so para o registro e para a sincronizacao com o aparelho. */
public final class FusoHorario {

    public static final String PADRAO = "America/Sao_Paulo";

    private FusoHorario() {
    }

    /** Ausente cai no padrao; presente precisa existir na base de fusos. */
    public static String validar(String informado) {
        if (informado == null || informado.isBlank()) {
            return PADRAO;
        }
        try {
            return ZoneId.of(informado.trim()).getId();
        } catch (DateTimeException e) {
            throw ApiException.fusoHorarioInvalido(informado);
        }
    }
}
