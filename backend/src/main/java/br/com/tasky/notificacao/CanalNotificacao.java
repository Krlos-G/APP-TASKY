package br.com.tasky.notificacao;

import br.com.tasky.entity.InscricaoPush;

/**
 * Por onde um lembrete chega ao usuario.
 *
 * A interface existe para o resto do app nao conhecer a biblioteca de push: o
 * spec ja prevê Telegram e e-mail como canais adicionais na v1.1, e a lib atual
 * de Web Push e a peca de maior risco do projeto.
 */
public interface CanalNotificacao {

    /** False quando falta configuracao - o despachante nem tenta enviar. */
    boolean disponivel();

    ResultadoEnvio enviar(InscricaoPush inscricao, Notificacao notificacao);
}
