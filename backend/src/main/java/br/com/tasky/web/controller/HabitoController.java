package br.com.tasky.web.controller;

import br.com.tasky.service.HabitoService;
import br.com.tasky.web.dto.ArquivoRequest;
import br.com.tasky.web.dto.HabitoRequest;
import br.com.tasky.web.dto.HabitoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/habitos")
public class HabitoController {

    private final HabitoService habitoService;

    public HabitoController(HabitoService habitoService) {
        this.habitoService = habitoService;
    }

    @GetMapping
    public List<HabitoResponse> listar(
            @RequestParam(defaultValue = "false") boolean incluirArquivados) {
        return habitoService.listar(incluirArquivados);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitoResponse criar(@Valid @RequestBody HabitoRequest pedido) {
        return habitoService.criar(pedido);
    }

    @PutMapping("/{id}")
    public HabitoResponse atualizar(@PathVariable Long id,
                                    @Valid @RequestBody HabitoRequest pedido) {
        return habitoService.atualizar(id, pedido);
    }

    @PutMapping("/{id}/arquivo")
    public HabitoResponse arquivar(@PathVariable Long id,
                                   @Valid @RequestBody ArquivoRequest pedido) {
        return habitoService.definirArquivo(id, pedido.arquivado());
    }

    /** Apaga o habito e o historico junto - arquivar e que preserva. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@PathVariable Long id) {
        habitoService.apagar(id);
    }
}
