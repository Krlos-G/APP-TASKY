package br.com.tasky.web.controller;

import br.com.tasky.service.FiltroTarefa;
import br.com.tasky.service.TarefaService;
import br.com.tasky.web.dto.TarefaRequest;
import br.com.tasky.web.dto.TarefaResponse;
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
@RequestMapping("/api/v1/tarefas")
public class TarefaController {

    private final TarefaService tarefaService;

    public TarefaController(TarefaService tarefaService) {
        this.tarefaService = tarefaService;
    }

    @GetMapping
    public List<TarefaResponse> listar(@RequestParam(defaultValue = "HOJE") FiltroTarefa filtro) {
        return tarefaService.listar(filtro);
    }

    @GetMapping("/{id}")
    public TarefaResponse buscar(@PathVariable Long id) {
        return tarefaService.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarefaResponse criar(@Valid @RequestBody TarefaRequest pedido) {
        return tarefaService.criar(pedido);
    }

    @PutMapping("/{id}")
    public TarefaResponse atualizar(@PathVariable Long id, @Valid @RequestBody TarefaRequest pedido) {
        return tarefaService.atualizar(id, pedido);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@PathVariable Long id) {
        tarefaService.apagar(id);
    }

    @PutMapping("/{id}/conclusao")
    public TarefaResponse concluir(@PathVariable Long id) {
        return tarefaService.concluir(id);
    }

    @DeleteMapping("/{id}/conclusao")
    public TarefaResponse desfazerConclusao(@PathVariable Long id) {
        return tarefaService.desfazerConclusao(id);
    }
}
