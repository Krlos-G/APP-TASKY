package br.com.tasky.web.controller;

import br.com.tasky.service.RotinaService;
import br.com.tasky.web.dto.BlocoRequest;
import br.com.tasky.web.dto.ModeloDiaRequest;
import br.com.tasky.web.dto.ModeloDiaResponse;
import br.com.tasky.web.dto.SemanaRequest;
import br.com.tasky.web.dto.SemanaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rotina")
public class RotinaController {

    private final RotinaService rotinaService;

    public RotinaController(RotinaService rotinaService) {
        this.rotinaService = rotinaService;
    }

    @GetMapping("/modelos")
    public List<ModeloDiaResponse> listarModelos() {
        return rotinaService.listarModelos().stream().map(ModeloDiaResponse::de).toList();
    }

    @GetMapping("/modelos/{id}")
    public ModeloDiaResponse buscarModelo(@PathVariable Long id) {
        return ModeloDiaResponse.de(rotinaService.buscarModelo(id));
    }

    @PostMapping("/modelos")
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloDiaResponse criarModelo(@Valid @RequestBody ModeloDiaRequest pedido) {
        return ModeloDiaResponse.de(rotinaService.criarModelo(pedido));
    }

    @PutMapping("/modelos/{id}")
    public ModeloDiaResponse atualizarModelo(@PathVariable Long id,
                                             @Valid @RequestBody ModeloDiaRequest pedido) {
        return ModeloDiaResponse.de(rotinaService.atualizarModelo(id, pedido));
    }

    @DeleteMapping("/modelos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarModelo(@PathVariable Long id) {
        rotinaService.apagarModelo(id);
    }

    /** Devolve o modelo inteiro: o front precisa dos avisos de sobreposicao recalculados. */
    @PostMapping("/modelos/{id}/blocos")
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloDiaResponse adicionarBloco(@PathVariable Long id,
                                            @Valid @RequestBody BlocoRequest pedido) {
        return ModeloDiaResponse.de(rotinaService.adicionarBloco(id, pedido));
    }

    @PutMapping("/blocos/{id}")
    public ModeloDiaResponse atualizarBloco(@PathVariable Long id,
                                            @Valid @RequestBody BlocoRequest pedido) {
        return ModeloDiaResponse.de(rotinaService.atualizarBloco(id, pedido));
    }

    @DeleteMapping("/blocos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarBloco(@PathVariable Long id) {
        rotinaService.apagarBloco(id);
    }

    @GetMapping("/semana")
    public SemanaResponse verSemana() {
        return SemanaResponse.de(rotinaService.listarSemana());
    }

    @PutMapping("/semana")
    public SemanaResponse definirSemana(@Valid @RequestBody SemanaRequest pedido) {
        return SemanaResponse.de(rotinaService.definirSemana(pedido));
    }
}
