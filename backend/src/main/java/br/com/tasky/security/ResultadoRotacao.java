package br.com.tasky.security;

import br.com.tasky.entity.Usuario;

/**
 * Desfecho de uma tentativa de rotacao de refresh token.
 *
 * Interface selada para que o controlador seja obrigado a tratar todos os
 * casos: esquecer o replay silenciosamente seria justamente o bug que a
 * deteccao existe para evitar.
 */
public sealed interface ResultadoRotacao {

    /**
     * Rotacao bem-sucedida.
     *
     * @param corridaEmGraca true quando o token apresentado ja havia sido
     *                       rotacionado ha poucos segundos. Nao e ataque: sao
     *                       duas abas renovando ao mesmo tempo.
     */
    record Sucesso(Usuario usuario, TokenRefreshEmitido token, boolean corridaEmGraca)
            implements ResultadoRotacao {
    }

    /**
     * Um token antigo, revogado ha mais tempo que a janela de graca, foi
     * reapresentado. Sinal de vazamento: a familia inteira foi revogada.
     */
    record ReplayDetectado() implements ResultadoRotacao {
    }

    /** Token desconhecido ou vencido. Nada e revogado. */
    record Invalido() implements ResultadoRotacao {
    }
}
