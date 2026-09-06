package br.com.tasky.web.dto;

import br.com.tasky.entity.BlocoModelo;

import java.time.LocalTime;

public record BlocoResponse(
        Long id,
        String titulo,
        LocalTime horaInicio,
        LocalTime horaFim,
        String cor,
        Integer minutosAntecedenciaLembrete) {

    public static BlocoResponse de(BlocoModelo bloco) {
        return new BlocoResponse(
                bloco.getId(),
                bloco.getTitulo(),
                bloco.getHoraInicio(),
                bloco.getHoraFim(),
                bloco.getCor(),
                bloco.getMinutosAntecedenciaLembrete());
    }
}
