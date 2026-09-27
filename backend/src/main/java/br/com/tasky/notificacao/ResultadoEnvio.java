package br.com.tasky.notificacao;

public enum ResultadoEnvio {

    ENVIADO,

    /** O aparelho desinstalou o app ou revogou a permissao: apagar a inscricao. */
    INSCRICAO_EXPIRADA,

    /** Rede, limite de taxa ou erro do servico de push: vale tentar de novo. */
    FALHA_TEMPORARIA
}
