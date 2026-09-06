package br.com.tasky.security;

import br.com.tasky.config.AuthProperties;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.web.ApiException;
import br.com.tasky.web.dto.LoginRequest;
import br.com.tasky.web.dto.RegistrarRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
public class AuthService {

    private static final String FUSO_PADRAO = "America/Sao_Paulo";

    /**
     * Hash descartavel usado quando o e-mail nao existe.
     *
     * Sem isto, um login com e-mail inexistente responderia sem passar pelo
     * BCrypt e voltaria visivelmente mais rapido - diferenca suficiente para
     * alguem descobrir quais contas existem so medindo o tempo de resposta.
     */
    private final String hashDescartavel;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties propriedades;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       TokenAcessoService tokenAcessoService,
                       RefreshTokenService refreshTokenService,
                       AuthProperties propriedades) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.propriedades = propriedades;
        this.hashDescartavel = passwordEncoder.encode("senha-que-nunca-sera-usada");
    }

    @Transactional
    public Usuario registrar(RegistrarRequest pedido) {
        if (!propriedades.registroHabilitado()) {
            throw ApiException.registroDesabilitado();
        }

        if (!propriedades.codigoConvite().equals(pedido.codigoConvite())) {
            throw ApiException.conviteInvalido();
        }

        if (usuarioRepository.existsByEmailIgnoreCase(pedido.email())) {
            throw ApiException.emailJaCadastrado();
        }

        var usuario = new Usuario();
        usuario.setEmail(pedido.email().trim().toLowerCase());
        usuario.setSenhaHash(passwordEncoder.encode(pedido.senha()));
        usuario.setNomeExibicao(pedido.nomeExibicao().trim());
        usuario.setFusoHorario(validarFuso(pedido.fusoHorario()));

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Sessao login(LoginRequest pedido) {
        var encontrado = usuarioRepository.findByEmailIgnoreCase(pedido.email().trim());

        // Compara a senha mesmo quando o usuario nao existe, para que os dois
        // caminhos custem o mesmo tempo.
        String hashParaComparar = encontrado.map(Usuario::getSenhaHash).orElse(hashDescartavel);
        boolean senhaConfere = passwordEncoder.matches(pedido.senha(), hashParaComparar);

        if (encontrado.isEmpty() || !senhaConfere) {
            throw ApiException.credenciaisInvalidas();
        }

        return abrirSessao(encontrado.get());
    }

    @Transactional
    public Sessao renovar(String refreshTokenEmClaro) {
        var resultado = refreshTokenService.rotacionar(refreshTokenEmClaro);

        return switch (resultado) {
            case ResultadoRotacao.Sucesso sucesso -> new Sessao(
                    sucesso.usuario(),
                    tokenAcessoService.gerar(sucesso.usuario()),
                    sucesso.token());

            // Replay e sessao invalida devolvem a mesma resposta: contar ao
            // cliente que houve deteccao de replay so ajudaria um atacante.
            case ResultadoRotacao.ReplayDetectado ignorado -> throw ApiException.sessaoInvalida();
            case ResultadoRotacao.Invalido ignorado -> throw ApiException.sessaoInvalida();
        };
    }

    @Transactional
    public void logout(String refreshTokenEmClaro) {
        // Idempotente de proposito: sair duas vezes, ou com um token ja
        // invalido, nao e erro do ponto de vista de quem chamou.
        refreshTokenService.revogarFamiliaDoToken(refreshTokenEmClaro);
    }

    private Sessao abrirSessao(Usuario usuario) {
        return new Sessao(
                usuario,
                tokenAcessoService.gerar(usuario),
                refreshTokenService.emitirNovaFamilia(usuario));
    }

    private String validarFuso(String informado) {
        if (informado == null || informado.isBlank()) {
            return FUSO_PADRAO;
        }
        try {
            return ZoneId.of(informado.trim()).getId();
        } catch (DateTimeException e) {
            throw ApiException.fusoHorarioInvalido(informado);
        }
    }

    /** Resultado de um login ou renovacao: quem entrou e os dois tokens. */
    public record Sessao(Usuario usuario,
                         TokenAcessoService.TokenGerado accessToken,
                         TokenRefreshEmitido refreshToken) {
    }
}
