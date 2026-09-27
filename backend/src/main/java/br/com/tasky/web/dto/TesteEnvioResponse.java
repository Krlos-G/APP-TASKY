package br.com.tasky.web.dto;

/** @param aparelhos quantos estavam inscritos; enviadas pode ser menor */
public record TesteEnvioResponse(int enviadas, int aparelhos) {
}
