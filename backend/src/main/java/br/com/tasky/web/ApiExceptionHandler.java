package br.com.tasky.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Traduz excecoes em respostas JSON com formato estavel.
 *
 * Erros de validacao devolvem a lista de campos com problema, para o front
 * marcar cada campo em vez de mostrar um aviso generico.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErroCampo(String campo, String mensagem) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> tratarApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(MethodArgumentNotValidException e) {
        List<ErroCampo> campos = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroCampo(erro.getField(), erro.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(Map.of(
                "erro", "Dados invalidos.",
                "campos", campos));
    }

    /**
     * Duas edicoes simultaneas do mesmo registro - o @Version das entidades em
     * acao. O cliente precisa recarregar antes de tentar de novo, senao
     * sobrescreveria a alteracao do outro dispositivo sem perceber.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> tratarConflito(
            ObjectOptimisticLockingFailureException e) {
        log.debug("Conflito de edicao concorrente: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "erro", "Este item foi alterado em outro lugar. Recarregue e tente de novo."));
    }

    /**
     * URL que nao casa com nenhum endpoint.
     *
     * Sem este tratamento a excecao cairia no handler generico e viraria 500,
     * poluindo o log de erro com o que e apenas um caminho errado.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> tratarRotaInexistente(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("erro", "Recurso nao encontrado."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> tratarInesperado(Exception e) {
        // A causa vai para o log, nunca para a resposta: stack trace exposta
        // entrega detalhes internos a quem estiver sondando.
        log.error("Erro nao tratado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("erro", "Algo deu errado. Tente novamente."));
    }
}
