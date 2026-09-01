package br.com.tasky.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Exige um header proprietario nos endpoints que se autenticam por cookie.
 *
 * O /refresh e o /logout leem o refresh token do cookie, e o navegador envia
 * cookie automaticamente - o padrao classico vulneravel a CSRF. Com
 * SameSite=Lax o proprio navegador ja barra o POST cross-site, mas a politica
 * do cookie e configuravel neste projeto, e no modo SameSite=None (front e API
 * em origens diferentes) essa protecao desaparece.
 *
 * Um formulario malicioso em outro site consegue disparar o POST, mas nao
 * consegue adicionar header customizado sem passar por preflight de CORS - que
 * so autorizamos para a origem do proprio front. Por isso exigir o header e
 * suficiente, e vale nas duas configuracoes.
 */
@Component
public class ClienteHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Tasky-Client";
    public static final String VALOR_ESPERADO = "web";

    private static final String CAMINHO_REFRESH = "/api/v1/auth/refresh";
    private static final String CAMINHO_LOGOUT = "/api/v1/auth/logout";

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String caminho = request.getRequestURI();
        return !(CAMINHO_REFRESH.equals(caminho) || CAMINHO_LOGOUT.equals(caminho));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        if (!VALOR_ESPERADO.equals(request.getHeader(HEADER))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"erro":"Requisicao sem o cabecalho de cliente esperado."}""");
            return;
        }

        chain.doFilter(request, response);
    }
}
