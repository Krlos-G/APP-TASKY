package br.com.tasky.security;

import br.com.tasky.RelogioAjustavel;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testes do rate limit de login.
 *
 * Unitarios, sem subir contexto: o filtro so depende do Clock, e o
 * RelogioAjustavel permite atravessar a janela sem esperar um minuto real.
 */
class LoginRateLimitFilterTest {

    private static final Instant INICIO = Instant.parse("2026-09-01T10:00:00Z");
    private static final int MAXIMO = 10;

    private RelogioAjustavel relogio;
    private LoginRateLimitFilter filtro;

    @BeforeEach
    void preparar() {
        relogio = new RelogioAjustavel(INICIO);
        filtro = new LoginRateLimitFilter(relogio, MAXIMO);
    }

    private MockHttpServletRequest requisicaoDeLogin(String ip) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }

    private int tentarLogin(String ip) throws Exception {
        var response = new MockHttpServletResponse();
        filtro.doFilter(requisicaoDeLogin(ip), response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("as primeiras tentativas passam")
    void tentativasDentroDoLimitePassam() throws Exception {
        for (int i = 1; i <= MAXIMO; i++) {
            assertThat(tentarLogin("10.0.0.1"))
                    .as("tentativa %d deveria passar", i)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("a tentativa seguinte ao limite recebe 429 com Retry-After")
    void excedendoOLimiteRecebe429() throws Exception {
        for (int i = 0; i < MAXIMO; i++) {
            tentarLogin("10.0.0.1");
        }

        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filtro.doFilter(requisicaoDeLogin("10.0.0.1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("Muitas tentativas");
        // O importante: a requisicao nao chega ao endpoint de login.
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("passada a janela, as tentativas liberam de novo")
    void janelaDeslizanteLibera() throws Exception {
        for (int i = 0; i < MAXIMO; i++) {
            tentarLogin("10.0.0.1");
        }
        assertThat(tentarLogin("10.0.0.1")).isEqualTo(429);

        relogio.avancar(Duration.ofSeconds(61));

        assertThat(tentarLogin("10.0.0.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("a janela desliza, nao zera de uma vez")
    void janelaEDeslizante() throws Exception {
        // Cinco tentativas agora...
        for (int i = 0; i < 5; i++) {
            tentarLogin("10.0.0.1");
        }
        // ...e cinco meio minuto depois, fechando o limite.
        relogio.avancar(Duration.ofSeconds(30));
        for (int i = 0; i < 5; i++) {
            tentarLogin("10.0.0.1");
        }
        assertThat(tentarLogin("10.0.0.1")).isEqualTo(429);

        // Passados mais 31s, as cinco primeiras sairam da janela e ha espaco -
        // mas as cinco seguintes ainda contam.
        relogio.avancar(Duration.ofSeconds(31));
        for (int i = 0; i < 5; i++) {
            assertThat(tentarLogin("10.0.0.1")).isEqualTo(200);
        }
        assertThat(tentarLogin("10.0.0.1")).isEqualTo(429);
    }

    @Test
    @DisplayName("o limite e por IP: um cliente nao bloqueia o outro")
    void limitePorIp() throws Exception {
        for (int i = 0; i < MAXIMO; i++) {
            tentarLogin("10.0.0.1");
        }
        assertThat(tentarLogin("10.0.0.1")).isEqualTo(429);

        assertThat(tentarLogin("10.0.0.2")).isEqualTo(200);
    }

    @Test
    @DisplayName("o filtro so age no POST de login")
    void naoAfetaOutrasRotas() {
        var outraRota = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        var getNoLogin = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        var loginPost = new MockHttpServletRequest("POST", "/api/v1/auth/login");

        assertThat(filtro.shouldNotFilter(outraRota)).isTrue();
        assertThat(filtro.shouldNotFilter(getNoLogin)).isTrue();
        assertThat(filtro.shouldNotFilter(loginPost)).isFalse();
    }
}
