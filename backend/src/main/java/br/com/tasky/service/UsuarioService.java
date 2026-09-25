package br.com.tasky.service;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioAtual usuarioAtual;

    public UsuarioService(UsuarioRepository usuarioRepository, UsuarioAtual usuarioAtual) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioAtual = usuarioAtual;
    }

    /**
     * O fuso vem do aparelho a cada sessao, entao a maioria das chamadas nao
     * muda nada - gravar so quando mudou evita escrever por escrever.
     */
    @Transactional
    public void definirFuso(String informado) {
        String fuso = FusoHorario.validar(informado);
        Usuario usuario = usuarioAtual.obrigatorio();

        if (fuso.equals(usuario.getFusoHorario())) {
            return;
        }

        usuario.setFusoHorario(fuso);
        usuarioRepository.save(usuario);
    }
}
