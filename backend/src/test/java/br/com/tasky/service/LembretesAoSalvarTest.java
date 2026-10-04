package br.com.tasky.service;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LembretesAoSalvarTest {

    @Test
    @DisplayName("falha ao gerar os lembretes nao vira erro para quem salvou")
    void falhaNaoPropaga() {
        var usuario = new Usuario();
        usuario.setId(7L);

        var lembreteService = mock(LembreteService.class);
        doThrow(new IllegalStateException("banco fora")).when(lembreteService).materializar(usuario);

        var usuarioRepository = mock(UsuarioRepository.class);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        var transacoes = mock(PlatformTransactionManager.class);
        when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        var ouvinte = new LembretesAoSalvar(lembreteService, usuarioRepository, transacoes);

        assertThatCode(() -> ouvinte.gerar(new AgendaAlterada(7L))).doesNotThrowAnyException();
        verify(lembreteService).materializar(usuario);
        verify(transacoes).rollback(any());
    }
}
