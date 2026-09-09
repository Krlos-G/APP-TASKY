package br.com.tasky.service;

import br.com.tasky.entity.Usuario;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * "Hoje" e o dia no fuso da conta, nunca no do servidor: sem isso, quem usa o
 * app depois da meia-noite veria o dia errado conforme onde ele esta hospedado.
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
