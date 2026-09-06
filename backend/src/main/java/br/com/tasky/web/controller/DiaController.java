package br.com.tasky.web.controller;

import br.com.tasky.service.DiaAssembler;
import br.com.tasky.web.dto.DiaResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dia")
public class DiaController {

    private final DiaAssembler diaAssembler;

    public DiaController(DiaAssembler diaAssembler) {
        this.diaAssembler = diaAssembler;
    }

    /**
     * @param data opcional; ausente significa "hoje no fuso do usuario", que so
     *             o servidor sabe calcular corretamente
     */
    @GetMapping
    public DiaResponse verDia(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return diaAssembler.montar(data);
    }
}
