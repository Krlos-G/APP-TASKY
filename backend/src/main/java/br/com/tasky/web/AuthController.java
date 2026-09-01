package br.com.tasky.web;

import br.com.tasky.config.AuthProperties;
import br.com.tasky.security.AuthService;
import br.com.tasky.security.UsuarioAutenticado;
import br.com.tasky.web.dto.LoginRequest;
import br.com.tasky.web.dto.RegistrarRequest;
import br.com.tasky.web.dto.TokenResponse;
import br.com.tasky.web.dto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String COOKIE_REFRESH = "tasky_refresh";

    /**
     * O cookie so e enviado nas rotas de autenticacao.
     *
     * Limitar o Path significa que o refresh token nao acompanha as chamadas
     * comuns da API - se um endpoint qualquer vazar os headers num log, o token
     * de sessao nao estara la.
     */
    private static final String CAMINHO_COOKIE = "/api/v1/auth";

    private final AuthService authService;
    private final AuthProperties propriedades;

    public AuthController(AuthService authService, AuthProperties propriedades) {
        this.authService = authService;
        this.propriedades = propriedades;
    }

    @PostMapping("/registrar")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistrarRequest pedido) {
        var usuario = authService.registrar(pedido);
        // Sem sessao automatica: registrar e entrar sao acoes distintas, e o
        // front faz o login em seguida com as mesmas credenciais.
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(usuario));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest pedido) {
        return responderComSessao(authService.login(pedido));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.sessaoInvalida();
        }
        return responderComSessao(authService.renovar(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken) {

        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieDeRemocao().toString())
                .build();
    }

    @GetMapping("/eu")
    public UsuarioAutenticado eu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw ApiException.sessaoInvalida();
        }
        return usuario;
    }

    private ResponseEntity<TokenResponse> responderComSessao(AuthService.Sessao sessao) {
        var corpo = new TokenResponse(
                sessao.accessToken().valor(),
                sessao.accessToken().expiraEm());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieDeRefresh(sessao.refreshToken().valor()).toString())
                .body(corpo);
    }

    private ResponseCookie cookieDeRefresh(String valor) {
        return ResponseCookie.from(COOKIE_REFRESH, valor)
                // httpOnly mantem o token fora do alcance de qualquer script:
                // um XSS na pagina nao consegue ler a sessao.
                .httpOnly(true)
                .secure(propriedades.cookieSecure())
                .sameSite(propriedades.cookieSameSite())
                .path(CAMINHO_COOKIE)
                .maxAge(propriedades.validadeRefreshToken())
                .build();
    }

    private ResponseCookie cookieDeRemocao() {
        return ResponseCookie.from(COOKIE_REFRESH, "")
                .httpOnly(true)
                .secure(propriedades.cookieSecure())
                .sameSite(propriedades.cookieSameSite())
                .path(CAMINHO_COOKIE)
                .maxAge(Duration.ZERO)
                .build();
    }
}
