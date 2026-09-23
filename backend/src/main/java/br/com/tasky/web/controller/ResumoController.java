package br.com.tasky.web.controller;

import br.com.tasky.service.ResumoService;
import br.com.tasky.web.dto.ResumoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resumo")
public class ResumoController {

    private final ResumoService resumoService;

    public ResumoController(ResumoService resumoService) {
        this.resumoService = resumoService;
    }

    @GetMapping
    public ResumoResponse ver() {
        return resumoService.montar();
    }
}
