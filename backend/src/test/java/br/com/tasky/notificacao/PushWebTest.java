package br.com.tasky.notificacao;

import br.com.tasky.config.PushProperties;
import br.com.tasky.entity.InscricaoPush;
import nl.martijndwars.webpush.Utils;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A biblioteca de Web Push, provada sozinha.
 *
 * E a peca de maior risco do projeto: sem release desde fevereiro de 2025 e
 * nunca exercitada contra Java 21. O que importa aqui e que a criptografia e a
 * assinatura VAPID acontecem de verdade.
 *
 * Sem rede de proposito: nesta maquina o java.exe nao abre conexoes de
 * loopback (ver Fatia 1), e a preparePost faz todo o trabalho criptografico
 * antes de qualquer socket. A entrega real e verificada no navegador.
 */
class PushWebTest {

    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private static String vapidPublica;
    private static String vapidPrivada;

    @BeforeAll
    static void gerarChavesDoServidor() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair par = gerarPar();
        vapidPublica = BASE64.encodeToString(Utils.encode((ECPublicKey) par.getPublic()));
        vapidPrivada = BASE64.encodeToString(Utils.encode((ECPrivateKey) par.getPrivate()));
    }

    @Test
    @DisplayName("o envio sai assinado com VAPID e com o corpo cifrado")
    void envioAssinadoECifrado() throws Exception {
        PushWeb canal = canalHabilitado();
        assertThat(canal.disponivel()).isTrue();

        // Endpoint da Apple: e o que o iPhone do Carlos vai entregar.
        HttpPost envio = canal.prepararEnvio(
                inscricao("https://web.push.apple.com/QWxhZGRpbjpvcGVuIHNlc2FtZQ"),
                new Notificacao("Hora de ler", "20 paginas", "/hoje", "HABITO-7"));

        assertThat(envio.getURI())
                .hasToString("https://web.push.apple.com/QWxhZGRpbjpvcGVuIHNlc2FtZQ");
        assertThat(envio.getFirstHeader("Authorization").getValue()).startsWith("vapid");
        assertThat(envio.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
        assertThat(envio.getFirstHeader("TTL").getValue()).isEqualTo("1800");

        // O corpo precisa estar cifrado: o titulo nao pode aparecer nele.
        byte[] corpo = corpoDe(envio);
        assertThat(corpo).isNotEmpty();
        assertThat(new String(corpo, StandardCharsets.UTF_8)).doesNotContain("Hora de ler");
    }

    @Test
    @DisplayName("endpoint legado do FCM e reescrito para o padrao Web Push")
    void endpointLegadoDoFcm() throws Exception {
        HttpPost envio = canalHabilitado().prepararEnvio(
                inscricao("https://fcm.googleapis.com/fcm/send/abc123"),
                new Notificacao("t", "c", "/hoje", "tag"));

        // A lib troca /fcm/send/<id> por /wp/<id> sozinha. Fixado aqui porque
        // e o tipo de detalhe que assusta quando aparece num log de producao.
        assertThat(envio.getURI()).hasToString("https://fcm.googleapis.com/wp/abc123");
    }

    @ParameterizedTest(name = "status {0} -> {1}")
    @CsvSource({
            "200, ENVIADO",
            "201, ENVIADO",
            "404, INSCRICAO_EXPIRADA",
            "410, INSCRICAO_EXPIRADA",
            "429, FALHA_TEMPORARIA",
            "500, FALHA_TEMPORARIA",
    })
    @DisplayName("a resposta do servico de push vira decisao")
    void resultadoPorStatus(int status, ResultadoEnvio esperado) {
        assertThat(PushWeb.resultadoDe(status)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("sem chaves, o canal se declara indisponivel em vez de quebrar")
    void semChaves() throws Exception {
        PushWeb canal = new PushWeb(new PushProperties("", "", null), new ObjectMapper());

        assertThat(canal.disponivel()).isFalse();
        assertThat(canal.enviar(inscricao("https://exemplo/abc"),
                new Notificacao("t", "c", "/hoje", "tag")))
                .isEqualTo(ResultadoEnvio.FALHA_TEMPORARIA);
    }

    @Test
    @DisplayName("meia configuracao e recusada na subida")
    void metadeDasChaves() {
        assertThatThrownBy(() -> new PushProperties(vapidPublica, "", "mailto:eu@exemplo.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("as duas chaves VAPID ou nenhuma");
    }

    @Test
    @DisplayName("assunto precisa ser um contato de verdade")
    void assuntoInvalido() {
        assertThatThrownBy(() -> new PushProperties(vapidPublica, vapidPrivada, "carlos"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mailto:");
    }

    // ------------------------------------------------------------------ apoio

    private PushWeb canalHabilitado() throws Exception {
        return new PushWeb(
                new PushProperties(vapidPublica, vapidPrivada, "mailto:carlos@tasky.app"),
                new ObjectMapper());
    }

    /** Uma inscricao como o navegador entregaria: chave P-256 e segredo de 16 bytes. */
    private InscricaoPush inscricao(String endpoint) throws Exception {
        KeyPair doNavegador = gerarPar();
        byte[] segredo = new byte[16];
        new SecureRandom().nextBytes(segredo);

        var inscricao = new InscricaoPush();
        inscricao.setEndpoint(endpoint);
        inscricao.setP256dh(BASE64.encodeToString(
                Utils.encode((ECPublicKey) doNavegador.getPublic())));
        inscricao.setAuth(BASE64.encodeToString(segredo));
        return inscricao;
    }

    private static KeyPair gerarPar() throws Exception {
        KeyPairGenerator gerador =
                KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        gerador.initialize(new ECGenParameterSpec("prime256v1"));
        return gerador.generateKeyPair();
    }

    private static byte[] corpoDe(HttpPost envio) throws Exception {
        var saida = new ByteArrayOutputStream();
        envio.getEntity().writeTo(saida);
        return saida.toByteArray();
    }
}
