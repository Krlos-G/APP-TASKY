package br.com.tasky.notificacao;

import br.com.tasky.entity.InscricaoPush;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Canal de mentira: registra o que passou e devolve o que o teste mandar. */
public class CanalDeTeste implements CanalNotificacao {

    public final List<Notificacao> enviadas = new ArrayList<>();
    public final List<String> destinos = new ArrayList<>();

    public Function<InscricaoPush, ResultadoEnvio> resposta = inscricao -> ResultadoEnvio.ENVIADO;

    @Override
    public boolean disponivel() {
        return true;
    }

    @Override
    public ResultadoEnvio enviar(InscricaoPush inscricao, Notificacao notificacao) {
        enviadas.add(notificacao);
        destinos.add(inscricao.getEndpoint());
        return resposta.apply(inscricao);
    }

    public void limpar() {
        enviadas.clear();
        destinos.clear();
        resposta = inscricao -> ResultadoEnvio.ENVIADO;
    }
}
