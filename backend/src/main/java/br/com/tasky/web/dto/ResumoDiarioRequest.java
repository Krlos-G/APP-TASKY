package br.com.tasky.web.dto;

import java.time.LocalTime;

/** @param hora nula desliga o resumo diario */
public record ResumoDiarioRequest(LocalTime hora) {
}
