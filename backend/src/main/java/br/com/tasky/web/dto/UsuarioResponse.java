package br.com.tasky.web.dto;

import br.com.tasky.entity.Usuario;

public record UsuarioResponse(Long id, String email, String nomeExibicao, String fusoHorario) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNomeExibicao(),
                usuario.getFusoHorario());
    }
}
