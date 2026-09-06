package br.com.tasky.web.dto;

import br.com.tasky.entity.AtribuicaoDia;
import br.com.tasky.entity.enums.DiaSemana;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record SemanaResponse(Map<DiaSemana, ModeloResumido> modeloPorDia) {

    public record ModeloResumido(Long id, String nome) {
    }

    public static SemanaResponse de(List<AtribuicaoDia> atribuicoes) {
        Map<DiaSemana, AtribuicaoDia> porDia = atribuicoes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AtribuicaoDia::getDiaSemana, Function.identity()));

        // Devolve sempre os sete dias, com null onde nao ha rotina: assim o
        // front nao precisa saber a lista de dias nem tratar chave ausente.
        Map<DiaSemana, ModeloResumido> resultado = new LinkedHashMap<>();
        Arrays.stream(DiaSemana.values()).forEach(dia -> {
            AtribuicaoDia atribuicao = porDia.get(dia);
            resultado.put(dia, atribuicao == null ? null : new ModeloResumido(
                    atribuicao.getModeloDia().getId(),
                    atribuicao.getModeloDia().getNome()));
        });

        return new SemanaResponse(resultado);
    }
}
