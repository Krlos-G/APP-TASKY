package br.com.tasky.web.controller;

import br.com.tasky.service.InscricaoPushService;
import br.com.tasky.web.dto.ChavePushResponse;
import br.com.tasky.web.dto.InscricaoPushRequest;
import br.com.tasky.web.dto.TesteEnvioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inscricoes-push")
public class InscricaoPushController {

    private final InscricaoPushService inscricaoService;

    public InscricaoPushController(InscricaoPushService inscricaoService) {
        this.inscricaoService = inscricaoService;
    }

    /** A chave publica VAPID, que o navegador precisa para pedir a inscricao. */
    @GetMapping("/chave")
    public ChavePushResponse chave() {
        return inscricaoService.chave();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@Valid @RequestBody InscricaoPushRequest pedido) {
        inscricaoService.registrar(pedido);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@RequestParam String endpoint) {
        inscricaoService.remover(endpoint);
    }

    @PostMapping("/testar")
    public TesteEnvioResponse testar() {
        return inscricaoService.testar();
    }
}
