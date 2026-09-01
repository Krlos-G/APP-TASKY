package br.com.tasky.security;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da rotacao de refresh token.
 *
 * Rodam contra PostgreSQL real porque a seguranca da rotacao depende de
 * comportamento do banco - especialmente o UPDATE condicional que decide quem
 * ganha uma corrida entre duas requisicoes simultaneas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RefreshTokenServiceTest.RelogioDeTesteConfig.class})
class RefreshTokenServiceTest {

    private static final Instant INICIO = Instant.parse("2026-09-01T10:00:00Z");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(INICIO);
        }
    }

    @Autowired
    private RefreshTokenService servico;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private Clock clock;

    private RelogioAjustavel relogio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        relogio = (RelogioAjustavel) clock;
        relogio.definir(INICIO);

        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        var novo = new Usuario();
        novo.setEmail("carlos@exemplo.com");
        novo.setSenhaHash("nao-importa-neste-teste");
        novo.setNomeExibicao("Carlos");
        usuario = usuarioRepository.save(novo);
    }

    @Nested
    @DisplayName("caminho feliz")
    class CaminhoFeliz {

        @Test
        @DisplayName("o login emite um token de uma familia nova")
        void loginEmiteFamiliaNova() {
            var primeiro = servico.emitirNovaFamilia(usuario);
            var segundo = servico.emitirNovaFamilia(usuario);

            assertThat(primeiro.valor()).isNotBlank();
            assertThat(primeiro.expiraEm()).isAfter(INICIO);
            assertThat(primeiro.familia()).isNotEqualTo(segundo.familia());
        }

        @Test
        @DisplayName("o token em claro nunca e gravado no banco")
        void tokenEmClaroNaoVaiParaOBanco() {
            var emitido = servico.emitirNovaFamilia(usuario);

            var salvo = refreshTokenRepository.findAll().getFirst();
            assertThat(salvo.getTokenHash())
                    .isNotEqualTo(emitido.valor())
                    .hasSize(64); // SHA-256 em hexadecimal
        }

        @Test
        @DisplayName("rotacionar devolve um token novo, na mesma familia")
        void rotacaoMantemFamilia() {
            var original = servico.emitirNovaFamilia(usuario);

            var resultado = servico.rotacionar(original.valor());

            assertThat(resultado).isInstanceOfSatisfying(ResultadoRotacao.Sucesso.class, sucesso -> {
                assertThat(sucesso.usuario().getId()).isEqualTo(usuario.getId());
                assertThat(sucesso.token().valor()).isNotEqualTo(original.valor());
                assertThat(sucesso.token().familia()).isEqualTo(original.familia());
                assertThat(sucesso.corridaEmGraca()).isFalse();
            });
        }

        @Test
        @DisplayName("a sessao sobrevive a varias rotacoes encadeadas")
        void rotacaoEncadeada() {
            var atual = servico.emitirNovaFamilia(usuario);
            var familiaOriginal = atual.familia();

            for (int i = 0; i < 5; i++) {
                relogio.avancar(Duration.ofMinutes(20));
                var resultado = servico.rotacionar(atual.valor());
                assertThat(resultado).isInstanceOf(ResultadoRotacao.Sucesso.class);
                atual = ((ResultadoRotacao.Sucesso) resultado).token();
            }

            assertThat(atual.familia()).isEqualTo(familiaOriginal);
        }
    }

    @Nested
    @DisplayName("janela de graca")
    class JanelaDeGraca {

        @Test
        @DisplayName("duas abas renovando juntas nao derrubam a sessao")
        void corridaEntreAbasNaoDerrubaSessao() {
            var original = servico.emitirNovaFamilia(usuario);

            // Primeira aba rotaciona.
            var primeira = servico.rotacionar(original.valor());
            assertThat(primeira).isInstanceOf(ResultadoRotacao.Sucesso.class);

            // Segunda aba chega logo depois com o MESMO token ja usado.
            relogio.avancar(Duration.ofSeconds(5));
            var segunda = servico.rotacionar(original.valor());

            assertThat(segunda).isInstanceOfSatisfying(ResultadoRotacao.Sucesso.class, sucesso -> {
                assertThat(sucesso.corridaEmGraca()).isTrue();
                assertThat(sucesso.token().familia()).isEqualTo(original.familia());
            });
        }

        @Test
        @DisplayName("o token sucessor continua valido depois da corrida")
        void sucessorSobreviveAoReuso() {
            var original = servico.emitirNovaFamilia(usuario);
            var sucessor = ((ResultadoRotacao.Sucesso) servico.rotacionar(original.valor())).token();

            relogio.avancar(Duration.ofSeconds(5));
            servico.rotacionar(original.valor()); // corrida

            assertThat(servico.rotacionar(sucessor.valor()))
                    .isInstanceOf(ResultadoRotacao.Sucesso.class);
        }

        @Test
        @DisplayName("a graca vale ate o limite da janela")
        void gracaValeNaBorda() {
            var original = servico.emitirNovaFamilia(usuario);
            servico.rotacionar(original.valor());

            relogio.avancar(Duration.ofSeconds(60));

            assertThat(servico.rotacionar(original.valor()))
                    .isInstanceOf(ResultadoRotacao.Sucesso.class);
        }
    }

    @Nested
    @DisplayName("deteccao de replay")
    class DeteccaoDeReplay {

        @Test
        @DisplayName("um token antigo reaparecendo revoga a familia inteira")
        void replayRevogaFamilia() {
            var roubado = servico.emitirNovaFamilia(usuario);
            var atual = ((ResultadoRotacao.Sucesso) servico.rotacionar(roubado.valor())).token();

            // Passada a graca, o token antigo so pode ter vindo de uma copia.
            relogio.avancar(Duration.ofMinutes(5));
            assertThat(servico.rotacionar(roubado.valor()))
                    .isInstanceOf(ResultadoRotacao.ReplayDetectado.class);

            // O atacante perde o acesso, mas o usuario legitimo tambem: e o
            // comportamento desejado, porque nao ha como distinguir os dois.
            assertThat(servico.rotacionar(atual.valor()))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
        }

        @Test
        @DisplayName("o replay nao afeta outras sessoes do mesmo usuario")
        void replayNaoAfetaOutrosDispositivos() {
            var celular = servico.emitirNovaFamilia(usuario);
            var pc = servico.emitirNovaFamilia(usuario);

            var novoCelular = ((ResultadoRotacao.Sucesso) servico.rotacionar(celular.valor())).token();
            relogio.avancar(Duration.ofMinutes(5));
            servico.rotacionar(celular.valor()); // replay no celular

            assertThat(servico.rotacionar(novoCelular.valor()))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
            assertThat(servico.rotacionar(pc.valor()))
                    .isInstanceOf(ResultadoRotacao.Sucesso.class);
        }
    }

    @Nested
    @DisplayName("encerramento de sessao")
    class EncerramentoDeSessao {

        @Test
        @DisplayName("depois do logout o token nao renova mais")
        void logoutInvalidaOToken() {
            var token = servico.emitirNovaFamilia(usuario);

            servico.revogarFamilia(token.familia());

            assertThat(servico.rotacionar(token.valor()))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
        }

        @Test
        @DisplayName("a janela de graca nao desfaz um logout")
        void gracaNaoRessuscitaSessaoEncerrada() {
            // Esta e a armadilha que a verificacao de familia viva evita: sem
            // ela, um refresh logo apos o logout cairia na graca e receberia um
            // token novo, anulando o logout.
            var token = servico.emitirNovaFamilia(usuario);
            servico.revogarFamilia(token.familia());

            relogio.avancar(Duration.ofSeconds(5));

            assertThat(servico.rotacionar(token.valor()))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
        }

        @Test
        @DisplayName("sair de todos os dispositivos encerra todas as familias")
        void revogarTodasAsSessoes() {
            var celular = servico.emitirNovaFamilia(usuario);
            var pc = servico.emitirNovaFamilia(usuario);

            servico.revogarTodasAsSessoes(usuario);

            assertThat(servico.rotacionar(celular.valor()))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
            assertThat(servico.rotacionar(pc.valor()))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
        }
    }

    @Nested
    @DisplayName("tokens invalidos")
    class TokensInvalidos {

        @Test
        @DisplayName("um token vencido nao renova e nao revoga nada")
        void tokenVencido() {
            var token = servico.emitirNovaFamilia(usuario);

            relogio.avancar(Duration.ofDays(61));

            assertThat(servico.rotacionar(token.valor()))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
            // Vencimento e rotina, nao ataque: nada foi revogado por isso.
            assertThat(refreshTokenRepository.findAll())
                    .allSatisfy(t -> assertThat(t.getRevogadoEm()).isNull());
        }

        @Test
        @DisplayName("entradas desconhecidas ou vazias sao recusadas")
        void entradasInvalidas() {
            assertThat(servico.rotacionar("token-que-nunca-existiu"))
                    .isInstanceOf(ResultadoRotacao.Invalido.class);
            assertThat(servico.rotacionar(null)).isInstanceOf(ResultadoRotacao.Invalido.class);
            assertThat(servico.rotacionar("   ")).isInstanceOf(ResultadoRotacao.Invalido.class);
        }
    }
}
