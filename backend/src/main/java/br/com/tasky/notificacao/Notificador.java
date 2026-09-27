package br.com.tasky.notificacao;

import br.com.tasky.entity.InscricaoPush;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.InscricaoPushRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manda uma notificacao para todos os aparelhos do usuario.
 *
 * Existe separado do canal porque duas coisas precisam dele: o "enviar teste"
 * dos ajustes e o despachante de lembretes.
 */
@Component
public class Notificador {

    private final InscricaoPushRepository repository;
    private final CanalNotificacao canal;

    public Notificador(InscricaoPushRepository repository, CanalNotificacao canal) {
        this.repository = repository;
        this.canal = canal;
    }

    @Transactional
    public Resultado enviarParaTodos(Usuario usuario, Notificacao notificacao) {
        int enviadas = 0;
        int expiradas = 0;
        int falhas = 0;

        for (InscricaoPush inscricao : repository.findByUsuarioId(usuario.getId())) {
            switch (canal.enviar(inscricao, notificacao)) {
                case ENVIADO -> enviadas++;
                case INSCRICAO_EXPIRADA -> {
                    // O aparelho desinstalou o app: insistir nele nunca vai dar certo.
                    repository.delete(inscricao);
                    expiradas++;
                }
                case FALHA_TEMPORARIA -> falhas++;
            }
        }

        return new Resultado(enviadas, expiradas, falhas);
    }

    /**
     * @param falhas maior que zero e o unico caso em que vale tentar de novo -
     *               expirada ja foi apagada e enviada esta entregue
     */
    public record Resultado(int enviadas, int expiradas, int falhas) {

        public int aparelhos() {
            return enviadas + expiradas + falhas;
        }
    }
}
