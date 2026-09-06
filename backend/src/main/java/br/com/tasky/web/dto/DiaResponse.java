package br.com.tasky.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A linha do tempo de um dia.
 *
 * As listas de habitos e tarefas ja existem no contrato, mesmo vazias: elas
 * chegam nas Fatias 4 e 5, e reservar o espaco agora evita quebrar o contrato
 * e reescrever a tela Hoje duas vezes.
 *
 * @param data          o dia representado, no fuso do usuario
 * @param diaSemana     resolvido no servidor, nao no cliente
 * @param temRotina     false quando nenhum modelo esta atribuido a este dia -
 *                      estado legitimo, nao erro, e a tela precisa distinguir
 *                      "sem rotina" de "rotina vazia"
 * @param nomeDoModelo  nulo quando nao ha rotina
 */
public record DiaResponse(
        LocalDate data,
        String diaSemana,
        boolean temRotina,
        String nomeDoModelo,
        List<BlocoResponse> blocos,
        List<Object> habitos,
        List<Object> tarefas) {

    public static DiaResponse semRotina(LocalDate data, String diaSemana) {
        return new DiaResponse(data, diaSemana, false, null, List.of(), List.of(), List.of());
    }

    public static DiaResponse comRotina(LocalDate data, String diaSemana,
                                        String nomeDoModelo, List<BlocoResponse> blocos) {
        return new DiaResponse(data, diaSemana, true, nomeDoModelo, blocos, List.of(), List.of());
    }
}
