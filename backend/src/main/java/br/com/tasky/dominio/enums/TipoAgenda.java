package br.com.tasky.dominio.enums;

/** Como a recorrencia de um habito e definida. */
public enum TipoAgenda {
    /** Todo dia. */
    DIARIO,
    /** Em dias fixos da semana (usa dias_semana). */
    DIAS_SEMANA,
    /** N vezes por semana, sem dia fixo (usa meta_semanal). Fora do MVP. */
    VEZES_POR_SEMANA
}
