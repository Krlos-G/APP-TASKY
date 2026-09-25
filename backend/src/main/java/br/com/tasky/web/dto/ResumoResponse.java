package br.com.tasky.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param dia     os mesmos numeros que a tela Hoje mostra, vindos dos mesmos
 *                servicos - nao de uma contagem paralela
 * @param semana  de segunda ate hoje: contar os sete dias numa quarta pareceria
 *                fracasso quando ainda faltam quatro
 */
public record ResumoResponse(
        LocalDate data,
        Dia dia,
        Semana semana,
        List<Sequencia> streaks) {

    public record Dia(
            int habitosFeitos,
            int habitosDevidos,
            int tarefasFeitas,
            int tarefasDoDia,
            int atrasadas) {
    }

    public record Semana(
            LocalDate inicio,
            LocalDate fim,
            int habitosFeitos,
            int habitosCobrados,
            long tarefasConcluidas) {
    }

    public record Sequencia(Long id, String nome, String cor, int streak) {
    }
}
