package br.com.tasky.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A linha do tempo de um dia.
 *
 * @param temRotina    false quando nenhum modelo esta atribuido a este dia -
 *                     estado legitimo, nao erro, e a tela precisa distinguir
 *                     "sem rotina" de "rotina vazia"
 * @param nomeDoModelo nulo quando nao ha rotina
 * @param tarefas      reservado para a Fatia 5
 */
public record DiaResponse(
        LocalDate data,
        String diaSemana,
        boolean temRotina,
        String nomeDoModelo,
        List<BlocoResponse> blocos,
        List<HabitoDoDiaResponse> habitos,
        List<Object> tarefas) {

    public static DiaResponse semRotina(LocalDate data, String diaSemana,
                                        List<HabitoDoDiaResponse> habitos) {
        return new DiaResponse(data, diaSemana, false, null, List.of(), habitos, List.of());
    }

    public static DiaResponse comRotina(LocalDate data, String diaSemana, String nomeDoModelo,
                                        List<BlocoResponse> blocos,
                                        List<HabitoDoDiaResponse> habitos) {
        return new DiaResponse(data, diaSemana, true, nomeDoModelo, blocos, habitos, List.of());
    }
}
