package br.com.tasky.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chaves VAPID do Web Push.
 *
 * Vazias desabilitam as notificacoes sem impedir a aplicacao de subir: em
 * desenvolvimento nem sempre se quer um par de chaves configurado, e o resto do
 * app nao depende disso para funcionar.
 */
@ConfigurationProperties(prefix = "tasky.push")
public record PushProperties(String chavePublica, String chavePrivada, String assunto) {

    public PushProperties {
        boolean umaSo = preenchida(chavePublica) != preenchida(chavePrivada);
        if (umaSo) {
            throw new IllegalStateException(
                    "tasky.push exige as duas chaves VAPID ou nenhuma. "
                            + "Defina TASKY_VAPID_PUBLICA e TASKY_VAPID_PRIVADA.");
        }

        // O protocolo exige um contato de quem opera o servidor: e por ele que
        // o servico de push avisa quando algo esta errado com os envios.
        if (preenchida(chavePublica)
                && (assunto == null
                        || !(assunto.startsWith("mailto:") || assunto.startsWith("https://")))) {
            throw new IllegalStateException(
                    "tasky.push.assunto precisa comecar com mailto: ou https://. Recebido: "
                            + assunto);
        }
    }

    public boolean habilitado() {
        return preenchida(chavePublica);
    }

    private static boolean preenchida(String valor) {
        return valor != null && !valor.isBlank();
    }
}
