package br.com.tasky.notificacao;

import br.com.tasky.config.PushProperties;
import br.com.tasky.entity.InscricaoPush;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Map;

@Component
public class PushWeb implements CanalNotificacao {

    /**
     * Quanto tempo o servico de push guarda o lembrete se o aparelho estiver
     * offline. Meia hora, o mesmo limite com que o despachante descarta um
     * lembrete atrasado - passou disso, o empurrao virou ruido.
     */
    private static final int TTL_SEGUNDOS = 1800;

    private static final Logger log = LoggerFactory.getLogger(PushWeb.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushService pushService;
    private final ObjectMapper json;

    public PushWeb(PushProperties propriedades, ObjectMapper json) throws Exception {
        this.json = json;
        this.pushService = propriedades.habilitado()
                ? new PushService(propriedades.chavePublica(), propriedades.chavePrivada(),
                        propriedades.assunto())
                : null;

        if (pushService == null) {
            log.warn("Notificacoes desligadas: sem chaves VAPID configuradas.");
        }
    }

    @Override
    public boolean disponivel() {
        return pushService != null;
    }

    @Override
    public ResultadoEnvio enviar(InscricaoPush inscricao, Notificacao notificacao) {
        if (!disponivel()) {
            return ResultadoEnvio.FALHA_TEMPORARIA;
        }

        try {
            var resposta = pushService.send(montar(inscricao, notificacao), Encoding.AES128GCM);
            return resultadoDe(resposta.getStatusLine().getStatusCode());
        } catch (Exception e) {
            log.warn("Falha ao enviar push para a inscricao {}: {}", inscricao.getId(), e.toString());
            return ResultadoEnvio.FALHA_TEMPORARIA;
        }
    }

    /** Visivel para teste: e aqui que a criptografia e a assinatura acontecem. */
    HttpPost prepararEnvio(InscricaoPush inscricao, Notificacao notificacao) throws Exception {
        return pushService.preparePost(montar(inscricao, notificacao), Encoding.AES128GCM);
    }

    static ResultadoEnvio resultadoDe(int status) {
        if (status >= 200 && status < 300) {
            return ResultadoEnvio.ENVIADO;
        }
        // 404 e 410 sao a forma de o servico de push dizer que o aparelho
        // sumiu; insistir nele nunca vai dar certo.
        if (status == 404 || status == 410) {
            return ResultadoEnvio.INSCRICAO_EXPIRADA;
        }
        return ResultadoEnvio.FALHA_TEMPORARIA;
    }

    private Notification montar(InscricaoPush inscricao, Notificacao notificacao) throws Exception {
        return new Notification(
                inscricao.getEndpoint(),
                inscricao.getP256dh(),
                inscricao.getAuth(),
                payload(notificacao),
                TTL_SEGUNDOS);
    }

    /**
     * O formato do service worker do Angular: ele exibe a notificacao e trata o
     * clique sozinho quando o JSON vem assim.
     */
    private byte[] payload(Notificacao notificacao) {
        Map<String, Object> corpo = Map.of("notification", Map.of(
                "title", notificacao.titulo(),
                "body", notificacao.corpo(),
                "tag", notificacao.tag(),
                "data", Map.of("onActionClick", Map.of("default", Map.of(
                        "operation", "navigateLastFocusedOrOpen",
                        "url", notificacao.url())))));

        return json.writeValueAsString(corpo).getBytes(StandardCharsets.UTF_8);
    }
}
