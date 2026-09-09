package br.com.tasky.web;

import org.springframework.http.HttpStatus;

/** Erro de negocio que ja sabe com qual status HTTP deve ser respondido. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException credenciaisInvalidas() {
        // Mensagem deliberadamente vaga: dizer "e-mail nao encontrado" contaria
        // a quem sonda quais contas existem.
        return new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha invalidos.");
    }

    public static ApiException registroDesabilitado() {
        return new ApiException(HttpStatus.FORBIDDEN, "O registro esta desabilitado.");
    }

    public static ApiException conviteInvalido() {
        return new ApiException(HttpStatus.FORBIDDEN, "Codigo de convite invalido.");
    }

    public static ApiException emailJaCadastrado() {
        return new ApiException(HttpStatus.CONFLICT, "Ja existe uma conta com este e-mail.");
    }

    public static ApiException sessaoInvalida() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Sessao expirada. Entre novamente.");
    }

    /** Recurso inexistente OU de outro dono - o cliente nao distingue os casos. */
    public static ApiException naoEncontrado(String oQue) {
        return new ApiException(HttpStatus.NOT_FOUND, oQue + " nao encontrado.");
    }

    public static ApiException modeloEmUso(String dias) {
        return new ApiException(HttpStatus.CONFLICT,
                "Este modelo esta em uso em: " + dias + ". Troque o modelo desses dias antes de apaga-lo.");
    }

    public static ApiException horarioInvalido() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "A hora de fim precisa ser depois da hora de inicio. "
                        + "Blocos que atravessam a meia-noite ainda nao sao suportados.");
    }

    public static ApiException agendaNaoSuportada() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "A agenda por vezes na semana ainda nao esta disponivel. "
                        + "Escolha todo dia ou dias fixos da semana.");
    }

    public static ApiException diasDaSemanaVazios() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "Escolha ao menos um dia da semana para este habito.");
    }

    public static ApiException fusoHorarioInvalido(String valor) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Fuso horario desconhecido: " + valor);
    }
}
