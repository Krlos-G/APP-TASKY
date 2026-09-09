package br.com.tasky.service;

import br.com.tasky.entity.Habito;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.TipoAgenda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra do streak, sem banco e sem HTTP.
 *
 * Cada caso e uma combinacao de agenda, historico e data - o formato de tabela
 * deixa a regra inteira visivel de uma vez, que e o que importa numa peca em
 * que o erro nao aparece como excecao, e sim como um numero errado na tela.
 */
class StreakCalculatorTest {

    /** Segunda-feira. A escolha importa: os casos de DIAS_SEMANA dependem dela. */
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 7);
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final StreakCalculator calculadora = new StreakCalculator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    @DisplayName("contagem de dias seguidos")
    void contagem(Caso caso) {
        int streak = calculadora.calcular(caso.habito(), caso.marcacoes(), HOJE, ZONA);

        assertThat(streak).isEqualTo(caso.esperado());
    }

    static Stream<Caso> casos() {
        return Stream.of(
                caso("tres dias seguidos contam tres",
                        diario(), marcacoes().feito(0).feito(1).feito(2), 3),

                caso("nada marcado, nenhum streak",
                        diario(), marcacoes(), 0),

                caso("pulado nao soma, mas atravessa sem quebrar",
                        diario(), marcacoes().feito(0).pulado(1).feito(2), 2),

                caso("dia devido sem marcacao quebra a corrente",
                        diario(), marcacoes().feito(0).feito(2).feito(3), 1),

                // O caso que faz o numero nao piscar para zero toda meia-noite.
                caso("hoje ainda em aberto nao quebra o streak de ontem",
                        diario(), marcacoes().feito(1).feito(2).feito(3), 3),

                caso("marcar hoje soma ao streak de ontem",
                        diario(), marcacoes().feito(0).feito(1).feito(2).feito(3), 4),

                caso("so hoje marcado vale um",
                        diario(), marcacoes().feito(0), 1),

                // HOJE e segunda; o habito e de terca e quinta.
                caso("segunda nao cobra habito de terca e quinta",
                        criadoEm(nosDias(DiaSemana.TER, DiaSemana.QUI), HOJE.minusDays(6)),
                        marcacoes().feito(4).feito(6), 2),

                caso("a corrente quebra no dia devido em branco, nao no dia livre",
                        nosDias(DiaSemana.TER, DiaSemana.QUI), marcacoes().feito(4), 1),

                // Quarta nao e dia do habito: marcar la e bonus.
                caso("marcacao em dia nao devido nao soma nem quebra",
                        criadoEm(nosDias(DiaSemana.TER, DiaSemana.QUI), HOJE.minusDays(6)),
                        marcacoes().feito(4).feito(5).feito(6), 2),

                // Arquivado na sexta: o streak fica congelado la, nao cai com o
                // tempo. E o que faz arquivar preservar o historico de verdade.
                caso("habito arquivado congela o streak na data do arquivamento",
                        arquivadoEm(diario(), HOJE.minusDays(3)),
                        marcacoes().feito(3).feito(4).feito(5), 3),

                caso("habito criado ontem nao e cobrado por anteontem",
                        criadoEm(diario(), HOJE.minusDays(1)), marcacoes().feito(1), 1),

                caso("a contagem para no teto de um ano",
                        diario(), seguidosPorDia(400), 366));
    }

    // ------------------------------------------------------------------ apoio

    /** Um caso da tabela. O nome vira o titulo do teste. */
    record Caso(String nome, Habito habito, Map<LocalDate, StatusRegistroHabito> marcacoes,
                int esperado) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private static Caso caso(String nome, Habito habito, Marcacoes marcacoes, int esperado) {
        return new Caso(nome, habito, marcacoes.mapa(), esperado);
    }

    private static Habito diario() {
        return novo(TipoAgenda.DIARIO, EnumSet.noneOf(DiaSemana.class));
    }

    private static Habito nosDias(DiaSemana... dias) {
        return novo(TipoAgenda.DIAS_SEMANA, EnumSet.copyOf(List.of(dias)));
    }

    private static Habito novo(TipoAgenda agenda, Set<DiaSemana> dias) {
        var habito = new Habito();
        habito.setNome("Ler");
        habito.setTipoAgenda(agenda);
        habito.setDiasSemana(dias);
        // Longe o bastante para nao limitar a contagem, salvo onde o caso quer.
        habito.setCriadoEm(instante(HOJE.minusDays(500)));
        return habito;
    }

    private static Habito criadoEm(Habito habito, LocalDate data) {
        habito.setCriadoEm(instante(data));
        return habito;
    }

    private static Habito arquivadoEm(Habito habito, LocalDate data) {
        habito.setArquivadoEm(instante(data).plusSeconds(20 * 3600));
        return habito;
    }

    private static Instant instante(LocalDate data) {
        return data.atStartOfDay(ZONA).toInstant();
    }

    private static Marcacoes marcacoes() {
        return new Marcacoes();
    }

    private static Marcacoes seguidosPorDia(int quantidade) {
        var marcacoes = new Marcacoes();
        for (int dia = 0; dia < quantidade; dia++) {
            marcacoes.feito(dia);
        }
        return marcacoes;
    }

    /** Historico montado em dias atras, que le melhor que datas absolutas. */
    private static final class Marcacoes {

        private final Map<LocalDate, StatusRegistroHabito> mapa = new HashMap<>();

        Marcacoes feito(int diasAtras) {
            return em(diasAtras, StatusRegistroHabito.FEITO);
        }

        Marcacoes pulado(int diasAtras) {
            return em(diasAtras, StatusRegistroHabito.PULADO);
        }

        private Marcacoes em(int diasAtras, StatusRegistroHabito status) {
            mapa.put(HOJE.minusDays(diasAtras), status);
            return this;
        }

        Map<LocalDate, StatusRegistroHabito> mapa() {
            return mapa;
        }
    }
}
