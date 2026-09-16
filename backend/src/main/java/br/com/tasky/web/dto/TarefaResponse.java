package br.com.tasky.web.dto;

import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.enums.Prioridade;
import br.com.tasky.entity.enums.StatusTarefa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record TarefaResponse(
        Long id,
        String titulo,
        String observacoes,
        Prioridade prioridade,
        Integer minutosEstimados,
        LocalDate dataLimite,
        LocalDate dataPlanejada,
        LocalTime horaPlanejada,
        LocalTime horaLembrete,
        StatusTarefa status,
        Instant concluidoEm,
        boolean atrasada,
        boolean vencida) {

    public static TarefaResponse de(Tarefa tarefa, LocalDate hoje) {
        return new TarefaResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getObservacoes(),
                tarefa.getPrioridade(),
                tarefa.getMinutosEstimados(),
                tarefa.getDataLimite(),
                tarefa.getDataPlanejada(),
                tarefa.getHoraPlanejada(),
                tarefa.getHoraLembrete(),
                tarefa.getStatus(),
                tarefa.getConcluidoEm(),
                tarefa.isAtrasada(hoje),
                tarefa.isVencida(hoje));
    }
}
