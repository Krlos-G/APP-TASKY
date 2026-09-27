package br.com.tasky.web.dto;

import br.com.tasky.entity.Usuario;

import java.time.LocalTime;

/** @param horaResumoDiario nula = resumo diario desligado */
public record PerfilResponse(
        String email,
        String nomeExibicao,
        String fusoHorario,
        LocalTime horaResumoDiario) {

    public static PerfilResponse de(Usuario usuario) {
        return new PerfilResponse(
                usuario.getEmail(),
                usuario.getNomeExibicao(),
                usuario.getFusoHorario(),
                usuario.getHoraResumoDiario());
    }
}
