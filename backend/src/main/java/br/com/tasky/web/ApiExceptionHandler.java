package br.com.tasky.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> tratarInesperado(Exception e) {
        // A causa vai para o log, nunca para a resposta: stack trace exposta
        // entrega detalhes internos a quem estiver sondando.
        log.error("Erro nao tratado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("erro", "Algo deu errado. Tente novamente."));
    }
}
