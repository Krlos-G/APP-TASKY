package br.com.tasky.web;

import br.com.tasky.security.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint minimo protegido.
 *
 * Existe para exercitar a configuracao de seguranca ponta a ponta enquanto os
 * endpoints de dominio nao existem. Sai do projeto quando a Fatia 3 trouxer as
 * primeiras rotas de verdade.
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return Map.of(
                "mensagem", "pong",
                "usuarioId", usuario.id(),
                "email", usuario.email());
    }
}
