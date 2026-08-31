package br.com.tasky.dominio.conversor;

import br.com.tasky.dominio.enums.DiaSemana;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Grava um conjunto de dias da semana como lista separada por virgula
 * (ex.: "SEG,QUA,SEX") na coluna habito.dias_semana.
 *
 * Escolhido em vez de tabela de juncao ou array nativo por ser um conjunto
 * pequeno e fixo. Se um dia for preciso consultar por dia da semana, isto
 * deve virar tabela de juncao.
 */
@Converter
public class ConversorDiasSemana implements AttributeConverter<Set<DiaSemana>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DiaSemana> dias) {
        if (dias == null || dias.isEmpty()) {
            return null;
        }
        return dias.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<DiaSemana> convertToEntityAttribute(String valor) {
        if (valor == null || valor.isBlank()) {
            return EnumSet.noneOf(DiaSemana.class);
        }
        return Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(parte -> !parte.isEmpty())
                .map(DiaSemana::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DiaSemana.class)));
    }
}
