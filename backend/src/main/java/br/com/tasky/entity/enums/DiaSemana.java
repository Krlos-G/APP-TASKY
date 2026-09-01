package br.com.tasky.entity.enums;

import java.time.DayOfWeek;

/** Dia da semana como gravado no banco (ver ck_atribuicao_dia_semana). */
public enum DiaSemana {
    SEG, TER, QUA, QUI, SEX, SAB, DOM;

    public static DiaSemana de(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }

    public DayOfWeek paraDayOfWeek() {
        return DayOfWeek.of(ordinal() + 1);
    }
}
