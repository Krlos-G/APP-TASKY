package br.com.tasky.web.controller;

import br.com.tasky.service.UsuarioService;
import br.com.tasky.web.dto.FusoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Sincronizacao de fundo: o app manda o fuso do aparelho ao abrir. */
    @PutMapping("/eu/fuso")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void definirFuso(@Valid @RequestBody FusoRequest pedido) {
        usuarioService.definirFuso(pedido.fusoHorario());
    }
}
