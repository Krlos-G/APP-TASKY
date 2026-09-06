package br.com.tasky.web.dto;

import br.com.tasky.entity.BlocoModelo;
import br.com.tasky.entity.ModeloDia;

import java.util.Comparator;
import java.util.List;

/**
 * Um modelo de dia com seus blocos.
 *
 * @param sobreposicoes pares de blocos cujos horarios se cruzam. Sobrepor e
 *                      permitido - as vezes e intencional, como um bloco de
 *                      foco dentro do expediente - entao isto e aviso, nao
 *                      erro, e quem calcula e o servidor.
 */
public record ModeloDiaResponse(
        Long id,
        String nome,
        boolean padrao,
        List<BlocoResponse> blocos,
        List<Sobreposicao> sobreposicoes) {

    public record Sobreposicao(Long primeiroBlocoId, Long segundoBlocoId, String descricao) {
    }

    public static ModeloDiaResponse de(ModeloDia modelo) {
        List<BlocoModelo> ordenados = modelo.getBlocos().stream()
                .sorted(Comparator.comparing(BlocoModelo::getHoraInicio)
                        .thenComparing(BlocoModelo::getHoraFim))
                .toList();

        return new ModeloDiaResponse(
                modelo.getId(),
                modelo.getNome(),
                modelo.isPadrao(),
                ordenados.stream().map(BlocoResponse::de).toList(),
                detectarSobreposicoes(ordenados));
    }

    /**
     * Compara cada bloco com o seguinte na ordem de inicio.
     *
     * Como a lista esta ordenada, basta olhar o vizinho: se o proximo comeca
     * antes de o atual terminar, ha cruzamento.
     */
    private static List<Sobreposicao> detectarSobreposicoes(List<BlocoModelo> ordenados) {
        return java.util.stream.IntStream.range(0, Math.max(0, ordenados.size() - 1))
                .mapToObj(i -> new BlocoModelo[] { ordenados.get(i), ordenados.get(i + 1) })
                .filter(par -> par[1].getHoraInicio().isBefore(par[0].getHoraFim()))
                .map(par -> new Sobreposicao(
                        par[0].getId(),
                        par[1].getId(),
                        "\"%s\" e \"%s\" se sobrepoem".formatted(
                                par[0].getTitulo(), par[1].getTitulo())))
                .toList();
    }
}
