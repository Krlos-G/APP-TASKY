import { agruparPorQuando } from './agrupar';
import { Tarefa } from './tarefa.models';

/** Terça-feira. */
const HOJE = '2026-10-06';

function tarefa(titulo: string, dataPlanejada: string | null): Tarefa {
  return {
    id: titulo.length * 100 + (dataPlanejada?.length ?? 0),
    titulo,
    observacoes: null,
    prioridade: 'MEDIA',
    minutosEstimados: null,
    dataLimite: null,
    dataPlanejada,
    horaPlanejada: null,
    horaLembrete: null,
    status: 'A_FAZER',
    concluidoEm: null,
    atrasada: false,
    vencida: false,
  };
}

function resumo(secoes: ReturnType<typeof agruparPorQuando>) {
  return secoes.map((s) => [s.titulo, s.tarefas.map((t) => t.titulo)]);
}

describe('agruparPorQuando', () => {
  it('separa em atrasadas, hoje, amanha, cada dia seguinte e sem data', () => {
    const secoes = agruparPorQuando(
      [
        tarefa('Renovar o dominio', '2026-09-24'),
        tarefa('Planejar a fatia', '2026-10-06'),
        tarefa('Pagar a luz', '2026-10-07'),
        tarefa('Dentista', '2026-10-09'),
        tarefa('Comprar presente', null),
      ],
      HOJE,
    );

    expect(resumo(secoes)).toEqual([
      ['Atrasadas', ['Renovar o dominio']],
      ['Hoje', ['Planejar a fatia']],
      ['Amanhã', ['Pagar a luz']],
      ['sexta, 9 de outubro', ['Dentista']],
      ['Sem data', ['Comprar presente']],
    ]);
  });

  it('so as atrasadas pedem atencao', () => {
    const secoes = agruparPorQuando(
      [tarefa('Velha', '2026-10-01'), tarefa('De hoje', '2026-10-06')],
      HOJE,
    );

    expect(secoes.map((s) => s.alerta)).toEqual([true, false]);
  });

  it('secao sem tarefa nao existe', () => {
    const secoes = agruparPorQuando([tarefa('So sem data', null)], HOJE);

    expect(resumo(secoes)).toEqual([['Sem data', ['So sem data']]]);
  });

  it('dia de outro ano leva o ano, para nao parecer desta semana', () => {
    const secoes = agruparPorQuando([tarefa('Renovar passaporte', '2027-01-05')], HOJE);

    expect(secoes[0].titulo).toBe('terça, 5 de janeiro de 2027');
  });

  it('a ordem das secoes nao depende da ordem em que as tarefas chegam', () => {
    const secoes = agruparPorQuando(
      [
        tarefa('Algum dia', null),
        tarefa('Sexta', '2026-10-09'),
        tarefa('Ontem', '2026-10-05'),
        tarefa('Hoje', '2026-10-06'),
      ],
      HOJE,
    );

    expect(secoes.map((s) => s.titulo)).toEqual([
      'Atrasadas',
      'Hoje',
      'sexta, 9 de outubro',
      'Sem data',
    ]);
  });

  it('dentro da secao, a ordem que veio do servidor fica', () => {
    const secoes = agruparPorQuando(
      [tarefa('Primeira', '2026-10-06'), tarefa('Segunda', '2026-10-06')],
      HOJE,
    );

    expect(secoes[0].tarefas.map((t) => t.titulo)).toEqual(['Primeira', 'Segunda']);
  });
});
