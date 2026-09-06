package br.com.tasky.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Configuracao de autenticacao, validada na subida da aplicacao.
 *
 * A validacao acontece no construtor compacto de proposito: e melhor a
 * aplicacao recusar iniciar do que subir com um segredo fraco ou com uma
 * combinacao de cookie que o navegador vai silenciosamente descartar.
 */
@Validated
@ConfigurationProperties(prefix = "tasky.auth")
public record AuthProperties(

        /** Chave HMAC do access token. Minimo de 32 bytes. */
        String jwtSecret,

        @NotNull Duration validadeAccessToken,

        @NotNull Duration validadeRefreshToken,

        /** Codigo exigido no registro. Vazio desabilita o registro. */
        String codigoConvite,

        /** Lax (origem unica) ou None (origens separadas). */
        @NotNull String cookieSameSite,

        boolean cookieSecure,

        /** Origem permitida no CORS. Vazio = sem CORS (caso de origem unica). */
        String corsOrigem,

        /** Tentativas de login permitidas por IP a cada minuto. */
        int maxTentativasLogin) {

    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    public AuthProperties {
        if (jwtSecret == null
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                    "tasky.auth.jwt-secret precisa ter no minimo " + TAMANHO_MINIMO_SEGREDO
                            + " bytes. Defina a variavel de ambiente TASKY_JWT_SECRET.");
        }

        if (!cookieSameSite.equalsIgnoreCase("Lax")
                && !cookieSameSite.equalsIgnoreCase("Strict")
                && !cookieSameSite.equalsIgnoreCase("None")) {
            throw new IllegalStateException(
                    "tasky.auth.cookie-same-site precisa ser Lax, Strict ou None. Recebido: "
                            + cookieSameSite);
        }

        // Os navegadores descartam SameSite=None sem Secure. Falhar aqui evita
        // um bug de "o login nao persiste" muito dificil de diagnosticar depois.
        if (maxTentativasLogin < 1) {
            throw new IllegalStateException(
                    "tasky.auth.max-tentativas-login precisa ser ao menos 1.");
        }

        if (cookieSameSite.equalsIgnoreCase("None") && !cookieSecure) {
            throw new IllegalStateException(
                    "cookie-same-site=None exige cookie-secure=true, "
                            + "caso contrario o navegador descarta o cookie.");
        }
    }

    /** O registro so fica aberto quando ha um codigo de convite configurado. */
    public boolean registroHabilitado() {
        return codigoConvite != null && !codigoConvite.isBlank();
    }

    public boolean corsHabilitado() {
        return corsOrigem != null && !corsOrigem.isBlank();
    }
}
