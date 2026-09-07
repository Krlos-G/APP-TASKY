package br.com.tasky.repository.projection;

import br.com.tasky.entity.enums.StatusRegistroHabito;

import java.time.LocalDate;

/**
 * Uma marcacao de habito, sem a entidade em volta.
 *
 * O calculo do streak so precisa de tres campos, e carregar RegistroHabito
 * inteiro traria junto a referencia preguicosa ao Habito - que estoura fora da
 * transacao, com open-in-view desligado. A projecao evita os dois problemas.
 *
 * @param habitoId lido da chave estrangeira, sem join para a tabela de habito
 */
public record Marcacao(Long habitoId, LocalDate data, StatusRegistroHabito status) {
}
