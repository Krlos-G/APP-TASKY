package br.com.tasky.security;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.web.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolve o Usuario da requisicao em curso.
 *
 * Existe para que nenhum servico precise mexer no SecurityContext nem repetir a
 * busca. O token carrega o id, mas as operacoes de dominio precisam da entidade
 * gerenciada - para associar registros novos e para ler o fuso horario.
 */
@Component
public class UsuarioAtual {

    private final UsuarioRepository usuarioRepository;

    public UsuarioAtual(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario obrigatorio() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao == null
                || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado autenticado)) {
            throw ApiException.sessaoInvalida();
        }

        // Conta apagada com token ainda valido: a sessao nao vale mais nada.
        return usuarioRepository.findById(autenticado.id())
                .orElseThrow(ApiException::sessaoInvalida);
    }

    public Long idObrigatorio() {
        return obrigatorio().getId();
    }
}
