package br.com.tasky.service;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.dto.PerfilResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioAtual usuarioAtual;
    private final ApplicationEventPublisher eventos;

    public UsuarioService(UsuarioRepository usuarioRepository, UsuarioAtual usuarioAtual,
                          ApplicationEventPublisher eventos) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioAtual = usuarioAtual;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfil() {
        return PerfilResponse.de(usuarioAtual.obrigatorio());
    }

    /** Hora nula desliga o resumo diario. */
    @Transactional
    public void definirResumoDiario(LocalTime hora) {
        Usuario usuario = usuarioAtual.obrigatorio();
        usuario.setHoraResumoDiario(hora);
        usuarioRepository.save(usuario);
        eventos.publishEvent(new AgendaAlterada(usuario.getId()));
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
        eventos.publishEvent(new AgendaAlterada(usuario.getId()));
    }
}
