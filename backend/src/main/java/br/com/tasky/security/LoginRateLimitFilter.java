package br.com.tasky.security;

import br.com.tasky.config.AuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de login por IP.
 *
 * Janela deslizante simples, em memoria: guarda o instante de cada tentativa e
 * conta quantas cabem na janela. Nao ha dependencia externa porque a aplicacao
 * roda em instancia unica - se um dia houver mais de uma replica, cada uma
 * contaria em separado e o limite efetivo seria multiplicado.
 *
 * O objetivo aqui e frear forca bruta, nao construir um WAF: um atacante com
 * varios IPs contorna isso. Para um app de um usuario so, e proporcional.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    private static final String CAMINHO_LOGIN = "/api/v1/auth/login";
    private static final Duration JANELA = Duration.ofMinutes(1);

    /** Acima disto, para de aceitar IPs novos: limite contra exaustao de memoria. */
    private static final int MAXIMO_IPS_RASTREADOS = 10_000;

    private final Map<String, Deque<Instant>> tentativasPorIp = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maximoTentativas;

    // Explicito porque ha mais de um construtor: sem isto o Spring nao sabe
    // qual usar e procura um sem argumentos.
    @Autowired
    public LoginRateLimitFilter(Clock clock, AuthProperties propriedades) {
        this(clock, propriedades.maxTentativasLogin());
    }

    /**
     * Construtor direto, para os testes fixarem o limite sem montar as
     * propriedades inteiras da aplicacao.
     */
    LoginRateLimitFilter(Clock clock, int maximoTentativas) {
        this.clock = clock;
        this.maximoTentativas = maximoTentativas;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
                && CAMINHO_LOGIN.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        Instant agora = clock.instant();

        if (excedeuLimite(ip, agora)) {
            log.warn("Rate limit de login atingido para o IP {}", ip);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(JANELA.toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"erro":"Muitas tentativas de login. Tente novamente em instantes."}""");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean excedeuLimite(String ip, Instant agora) {
        Instant inicioDaJanela = agora.minus(JANELA);

        // Impede que uma enxurrada de IPs distintos consuma memoria sem limite.
        if (tentativasPorIp.size() >= MAXIMO_IPS_RASTREADOS && !tentativasPorIp.containsKey(ip)) {
            limparIpsOciosos(inicioDaJanela);
        }

        Deque<Instant> tentativas = tentativasPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());

        // Sincroniza na propria fila: o ConcurrentHashMap protege o mapa, mas
        // nao o Deque de dentro dele.
        synchronized (tentativas) {
            while (!tentativas.isEmpty() && tentativas.peekFirst().isBefore(inicioDaJanela)) {
                tentativas.pollFirst();
            }

            if (tentativas.size() >= maximoTentativas) {
                return true;
            }

            tentativas.addLast(agora);
            return false;
        }
    }

    private void limparIpsOciosos(Instant inicioDaJanela) {
        tentativasPorIp.entrySet().removeIf(entrada -> {
            Deque<Instant> fila = entrada.getValue();
            synchronized (fila) {
                return fila.isEmpty() || fila.peekLast().isBefore(inicioDaJanela);
            }
        });
    }
}
