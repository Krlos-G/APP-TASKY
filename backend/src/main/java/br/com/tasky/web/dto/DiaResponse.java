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
 * @param atrasadas    so preenchida quando o dia pedido e hoje: olhando outro
 *                     dia, "atrasada em relacao a que" nao tem resposta util
 */
public record DiaResponse(
        LocalDate data,
        String diaSemana,
        boolean temRotina,
        String nomeDoModelo,
        List<BlocoResponse> blocos,
        List<HabitoDoDiaResponse> habitos,
        List<TarefaResponse> tarefas,
        List<TarefaResponse> atrasadas) {
}
