package br.com.tasky;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Clock controlado pelos testes.
 *
 * Permite verificar expiracao, janela de graca e virada de dia sem esperar o
 * tempo real passar. E a razao pela qual a aplicacao injeta Clock em vez de
 * chamar Instant.now() diretamente.
 */
public class RelogioAjustavel extends Clock {

    private Instant instante;
    private final ZoneId zona;

    public RelogioAjustavel(Instant inicial) {
        this(inicial, ZoneOffset.UTC);
    }

    private RelogioAjustavel(Instant inicial, ZoneId zona) {
        this.instante = inicial;
        this.zona = zona;
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId outraZona) {
        return new RelogioAjustavel(instante, outraZona);
    }

    @Override
    public Instant instant() {
        return instante;
    }

    public void avancar(Duration quanto) {
        instante = instante.plus(quanto);
    }

    public void definir(Instant novo) {
        instante = novo;
    }
}
