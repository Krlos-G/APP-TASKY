package br.com.tasky.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica a requisicao a partir do header Authorization: Bearer.
 *
 * O filtro nunca rejeita nada: token ausente ou invalido apenas deixa a
 * requisicao seguir sem autenticacao, e quem decide se isso e problema sao as
 * regras de autorizacao do SecurityConfig. Isso mantem rotas publicas
 * funcionando mesmo quando o navegador manda um token vencido.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final TokenAcessoService tokenAcessoService;

    public JwtAuthenticationFilter(TokenAcessoService tokenAcessoService) {
        this.tokenAcessoService = tokenAcessoService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        // Se ja ha autenticacao no contexto, nada a fazer: outro filtro cuidou.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extrairToken(request)
                    .flatMap(tokenAcessoService::validar)
                    .ifPresent(usuario -> autenticar(usuario, request));
        }

        chain.doFilter(request, response);
    }

    private java.util.Optional<String> extrairToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIXO)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(header.substring(PREFIXO.length()).trim());
    }

    private void autenticar(UsuarioAutenticado usuario, HttpServletRequest request) {
        // Sem authorities: o app tem um unico papel, e inventar ROLE_USER agora
        // so criaria cerimonia sem uso.
        var autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
        autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }
}
