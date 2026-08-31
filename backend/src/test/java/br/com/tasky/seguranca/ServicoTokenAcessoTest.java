package br.com.tasky.seguranca;

import br.com.tasky.config.PropriedadesAutenticacao;
import br.com.tasky.dominio.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Todo teste aqui usa Clock.fixed. E o que permite verificar expiracao sem
 * esperar 20 minutos, e a razao de o Clock ser injetado em vez de o codigo
 * chamar Instant.now().
 */
class ServicoTokenAcessoTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes-de-tamanho";
    private static final Instant AGORA = Instant.parse("2026-08-31T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofMinutes(20);

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuario = new Usuario();
        usuario.setId(42L);
        usuario.setEmail("carlos@exemplo.com");
        usuario.setNomeExibicao("Carlos");
    }

    private ServicoTokenAcesso servicoEm(Instant momento) {
        var propriedades = new PropriedadesAutenticacao(
                SEGREDO, VALIDADE, Duration.ofDays(60), "convite", "Lax", false, "");
        return new ServicoTokenAcesso(propriedades, Clock.fixed(momento, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("um token recem emitido devolve a identidade do usuario")
    void tokenValidoDevolveIdentidade() {
        var servico = servicoEm(AGORA);

        var gerado = servico.gerar(usuario);
        var identidade = servico.validar(gerado.valor());

        assertThat(identidade).contains(
                new UsuarioAutenticado(42L, "carlos@exemplo.com", "Carlos"));
        assertThat(gerado.expiraEm()).isEqualTo(AGORA.plus(VALIDADE));
    }

    @Test
    @DisplayName("o token ainda vale um instante antes de expirar")
    void tokenValidoNaBordaAntesDeExpirar() {
        var emissor = servicoEm(AGORA);
        var token = emissor.gerar(usuario).valor();

        var quaseExpirado = servicoEm(AGORA.plus(VALIDADE).minusSeconds(1));

        assertThat(quaseExpirado.validar(token)).isPresent();
    }

    @Test
    @DisplayName("o token e recusado depois de expirar")
    void tokenExpiradoERecusado() {
        var emissor = servicoEm(AGORA);
        var token = emissor.gerar(usuario).valor();

        var depois = servicoEm(AGORA.plus(VALIDADE).plusSeconds(60));

        assertThat(depois.validar(token)).isEmpty();
    }

    @Test
    @DisplayName("um token assinado com outra chave e recusado")
    void assinaturaDeOutraChaveERecusada() {
        var outraChave = Keys.hmacShaKeyFor(
                "uma-chave-completamente-diferente-e-com-32-bytes".getBytes(StandardCharsets.UTF_8));

        String forjado = Jwts.builder()
                .issuer("tasky")
                .subject("42")
                .issuedAt(Date.from(AGORA))
                .expiration(Date.from(AGORA.plus(VALIDADE)))
                .signWith(outraChave)
                .compact();

        assertThat(servicoEm(AGORA).validar(forjado)).isEmpty();
    }

    @Test
    @DisplayName("um token de outro emissor e recusado")
    void emissorDiferenteERecusado() {
        var chave = Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8));

        String deOutroEmissor = Jwts.builder()
                .issuer("outro-sistema")
                .subject("42")
                .issuedAt(Date.from(AGORA))
                .expiration(Date.from(AGORA.plus(VALIDADE)))
                .signWith(chave)
                .compact();

        assertThat(servicoEm(AGORA).validar(deOutroEmissor)).isEmpty();
    }

    @Test
    @DisplayName("um token adulterado e recusado")
    void tokenAdulteradoERecusado() {
        var servico = servicoEm(AGORA);
        String token = servico.gerar(usuario).valor();

        // Altera um caractere do payload, mantendo a estrutura do JWT.
        String[] partes = token.split("\\.");
        partes[1] = partes[1].substring(0, partes[1].length() - 2)
                + (partes[1].endsWith("A") ? "B" : "A");
        String adulterado = String.join(".", partes);

        assertThat(servico.validar(adulterado)).isEmpty();
    }

    @Test
    @DisplayName("entradas sem sentido sao recusadas sem lancar excecao")
    void entradasInvalidasSaoRecusadas() {
        var servico = servicoEm(AGORA);

        assertThat(servico.validar(null)).isEmpty();
        assertThat(servico.validar("")).isEmpty();
        assertThat(servico.validar("   ")).isEmpty();
        assertThat(servico.validar("nao-e-um-jwt")).isEmpty();
        assertThat(servico.validar("a.b.c")).isEmpty();
    }
}
