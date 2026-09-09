package br.com.tasky.service;

import br.com.tasky.entity.Usuario;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Que dia e hoje para este usuario.
 *
 * Existe para que a resposta seja sempre a mesma em todo o app. "Hoje" e o dia
 * no fuso da conta, nunca no do servidor: sem isso, quem usa o app depois da
 * meia-noite veria o dia seguinte (ou o anterior) dependendo de onde a
 * aplicacao esta hospedada - e o streak quebraria sozinho na virada.
 */
@Component
public class DataDoUsuario {

    private final Clock clock;

    public DataDoUsuario(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoje(Usuario usuario) {
        return LocalDate.ofInstant(clock.instant(), usuario.zona());
    }
}
