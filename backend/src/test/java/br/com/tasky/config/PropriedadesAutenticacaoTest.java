package br.com.tasky.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A aplicacao deve recusar subir com configuracao de autenticacao insegura.
 * Falhar na subida e muito melhor do que rodar com um segredo fraco ou com um
 * cookie que o navegador vai descartar em silencio.
 */
class PropriedadesAutenticacaoTest {

    private static final String SEGREDO_VALIDO = "um-segredo-de-teste-com-mais-de-32-bytes-ok";

    private PropriedadesAutenticacao criar(String segredo, String sameSite, boolean secure) {
        return new PropriedadesAutenticacao(
                segredo,
                Duration.ofMinutes(20),
                Duration.ofDays(60),
                "convite",
                sameSite,
                secure,
                "");
    }

    @Test
    @DisplayName("aceita uma configuracao valida")
    void aceitaConfiguracaoValida() {
        assertThatCode(() -> criar(SEGREDO_VALIDO, "Lax", false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recusa segredo com menos de 32 bytes")
    void recusaSegredoCurto() {
        assertThatThrownBy(() -> criar("curto-demais", "Lax", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("recusa segredo nulo")
    void recusaSegredoNulo() {
        assertThatThrownBy(() -> criar(null, "Lax", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("recusa SameSite desconhecido")
    void recusaSameSiteInvalido() {
        assertThatThrownBy(() -> criar(SEGREDO_VALIDO, "Talvez", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Lax, Strict ou None");
    }

    @Test
    @DisplayName("recusa SameSite=None sem Secure, que o navegador descartaria")
    void recusaNoneSemSecure() {
        assertThatThrownBy(() -> criar(SEGREDO_VALIDO, "None", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exige cookie-secure=true");
    }

    @Test
    @DisplayName("aceita SameSite=None quando Secure esta ligado")
    void aceitaNoneComSecure() {
        assertThatCode(() -> criar(SEGREDO_VALIDO, "None", true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("registro fica desabilitado quando nao ha codigo de convite")
    void registroDesabilitadoSemCodigo() {
        var comCodigo = criar(SEGREDO_VALIDO, "Lax", false);
        assertThat(comCodigo.registroHabilitado()).isTrue();

        var semCodigo = new PropriedadesAutenticacao(
                SEGREDO_VALIDO, Duration.ofMinutes(20), Duration.ofDays(60),
                "  ", "Lax", false, "");
        assertThat(semCodigo.registroHabilitado()).isFalse();
    }

    @Test
    @DisplayName("CORS so e habilitado quando ha origem configurada")
    void corsSoComOrigem() {
        assertThat(criar(SEGREDO_VALIDO, "Lax", false).corsHabilitado()).isFalse();

        var comOrigem = new PropriedadesAutenticacao(
                SEGREDO_VALIDO, Duration.ofMinutes(20), Duration.ofDays(60),
                "convite", "Lax", false, "https://tasky.app");
        assertThat(comOrigem.corsHabilitado()).isTrue();
    }
}
