package br.com.tasky.seguranca;

import br.com.tasky.config.PropriedadesAutenticacao;
import br.com.tasky.dominio.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emite e valida o access token (JWT assinado com HS256).
 *
 * O token e de vida curta e nao e revogavel: quem controla a sessao de fato e
 * o refresh token, que vive no banco e pode ser revogado. Por isso aqui nao ha
 * consulta ao banco - validar e apenas conferir assinatura, emissor e prazo.
 */
@Service
public class ServicoTokenAcesso {

    private static final Logger log = LoggerFactory.getLogger(ServicoTokenAcesso.class);

    private static final String EMISSOR = "tasky";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NOME = "nome";

    private final SecretKey chave;
    private final Duration validade;
    private final Clock clock;

    public ServicoTokenAcesso(PropriedadesAutenticacao propriedades, Clock clock) {
        // Exige no minimo 256 bits, o que casa com o minimo de 32 bytes ja
        // cobrado por PropriedadesAutenticacao na subida da aplicacao.
        this.chave = Keys.hmacShaKeyFor(propriedades.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.validade = propriedades.validadeAccessToken();
        this.clock = clock;
    }

    public TokenGerado gerar(Usuario usuario) {
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(validade);

        String valor = Jwts.builder()
                .issuer(EMISSOR)
                .subject(String.valueOf(usuario.getId()))
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_NOME, usuario.getNomeExibicao())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(chave)
                .compact();

        return new TokenGerado(valor, expiraEm);
    }

    /**
     * Devolve a identidade do token, ou vazio se ele for invalido por qualquer
     * motivo: assinatura adulterada, prazo vencido, emissor diferente ou
     * formato quebrado.
     *
     * Nao lanca excecao de proposito - para o filtro de autenticacao, "token
     * ruim" e simplesmente "requisicao nao autenticada", e a distincao entre os
     * motivos nao deve vazar para quem chamou.
     */
    public Optional<UsuarioAutenticado> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(EMISSOR)
                    // Sem isto o jjwt conferiria o prazo pelo relogio do sistema,
                    // e nao pelo Clock injetado - o que tornaria o vencimento
                    // impossivel de testar.
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new UsuarioAutenticado(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NOME, String.class)));

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Access token recusado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Token emitido, com o instante de expiracao para o cliente se programar. */
    public record TokenGerado(String valor, Instant expiraEm) {
    }
}
