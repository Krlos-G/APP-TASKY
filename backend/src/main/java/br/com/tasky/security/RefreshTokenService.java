package br.com.tasky.security;

import br.com.tasky.config.AuthProperties;
import br.com.tasky.entity.RefreshToken;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Emissao e rotacao de refresh tokens.
 *
 * Modelo: cada login abre uma familia, e todo refresh revoga o token usado e
 * emite outro na mesma familia. Isso da uma propriedade util - um token so
 * deveria ser usado uma vez. Se um token ja usado reaparece, ou alguem o
 * copiou, ou duas abas renovaram juntas; a janela de graca separa os dois
 * casos.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /**
     * Reapresentar um token revogado ha menos que isto e tratado como corrida
     * entre abas, nao como ataque. Sem esta folga, duas requisicoes disparadas
     * ao mesmo tempo derrubariam a sessao do proprio usuario.
     */
    private static final Duration JANELA_GRACA = Duration.ofSeconds(60);

    /** 256 bits de entropia: nao ha o que adivinhar por forca bruta. */
    private static final int BYTES_DO_TOKEN = 32;

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration validade;
    private final SecureRandom aleatorio = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               AuthProperties propriedades,
                               Clock clock) {
        this.repository = repository;
        this.validade = propriedades.validadeRefreshToken();
        this.clock = clock;
    }

    /** Abre uma nova familia. Usado no login. */
    @Transactional
    public TokenRefreshEmitido emitirNovaFamilia(Usuario usuario) {
        return emitirNaFamilia(usuario, UUID.randomUUID());
    }

    @Transactional
    public ResultadoRotacao rotacionar(String tokenEmClaro) {
        if (tokenEmClaro == null || tokenEmClaro.isBlank()) {
            return new ResultadoRotacao.Invalido();
        }

        var encontrado = repository.buscarPorHashComUsuario(hashDe(tokenEmClaro));
        if (encontrado.isEmpty()) {
            return new ResultadoRotacao.Invalido();
        }

        RefreshToken registro = encontrado.get();
        Instant agora = clock.instant();

        if (!registro.getExpiraEm().isAfter(agora)) {
            // Vencido nao e suspeito: e so uma sessao velha. Nao revoga a familia.
            return new ResultadoRotacao.Invalido();
        }

        Usuario usuario = registro.getUsuario();
        UUID familia = registro.getFamilia();

        if (registro.getRevogadoEm() != null) {
            // A familia so continua viva se algum token dela ainda estiver
            // ativo. Depois de um logout, ou de uma revogacao por replay, nao
            // resta nenhum - e conceder graca ali desfaria a revogacao.
            boolean familiaViva = repository.existeAtivoNaFamilia(familia, agora);

            if (!familiaViva) {
                log.debug("Refresh de sessao ja encerrada; familia {} nao tem token ativo", familia);
                return new ResultadoRotacao.Invalido();
            }

            boolean dentroDaGraca =
                    Duration.between(registro.getRevogadoEm(), agora).compareTo(JANELA_GRACA) <= 0;

            if (dentroDaGraca) {
                log.debug("Refresh reapresentado dentro da janela de graca; tratando como corrida");
                return new ResultadoRotacao.Sucesso(usuario, emitirNaFamilia(usuario, familia), true);
            }

            // Token antigo, fora da graca, mas a familia segue em uso: alguem
            // tem uma copia de um token que ja deveria ter sido descartado.
            int revogados = repository.revogarFamilia(familia, agora);
            log.warn("Replay de refresh token detectado. Familia {} revogada ({} tokens ativos).",
                    familia, revogados);
            return new ResultadoRotacao.ReplayDetectado();
        }

        // Revogacao condicional: se outra requisicao revogou entre a leitura e
        // este update, nenhuma linha muda e sabemos que perdemos a corrida.
        int linhasAfetadas = repository.revogarSeAtivo(registro.getId(), agora);
        boolean perdeuCorrida = linhasAfetadas == 0;

        if (perdeuCorrida) {
            log.debug("Corrida na rotacao: outra requisicao revogou o mesmo token primeiro");
        }

        return new ResultadoRotacao.Sucesso(
                usuario, emitirNaFamilia(usuario, familia), perdeuCorrida);
    }

    /** Encerra a sessao: revoga a familia inteira. */
    @Transactional
    public void revogarFamilia(UUID familia) {
        repository.revogarFamilia(familia, clock.instant());
    }

    /** Encerra todas as sessoes do usuario, em todos os dispositivos. */
    @Transactional
    public void revogarTodasAsSessoes(Usuario usuario) {
        repository.revogarTodosDoUsuario(usuario.getId(), clock.instant());
    }

    private TokenRefreshEmitido emitirNaFamilia(Usuario usuario, UUID familia) {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        aleatorio.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiraEm = clock.instant().plus(validade);

        var registro = new RefreshToken();
        registro.setUsuario(usuario);
        registro.setTokenHash(hashDe(valor));
        registro.setFamilia(familia);
        registro.setExpiraEm(expiraEm);
        repository.save(registro);

        return new TokenRefreshEmitido(valor, expiraEm, familia);
    }

    /**
     * SHA-256, e nao BCrypt, de proposito.
     *
     * BCrypt existe para tornar cara a forca bruta sobre segredos de baixa
     * entropia, como senhas humanas. Este token tem 256 bits aleatorios: nao ha
     * dicionario que o alcance. Usar BCrypt aqui so somaria latencia a cada
     * renovacao, sem ganho de seguranca.
     */
    private String hashDe(String valor) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
