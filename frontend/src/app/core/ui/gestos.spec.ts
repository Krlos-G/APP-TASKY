import { UrlSegment } from '@angular/router';
import { abaVizinha, casarAba, indiceDaAba, indiceDaSecao } from './abas';
import { decidirEixo, deveTrocar, indicePelaPosicao, velocidadeRecente } from './gestos';

describe('abas', () => {
  it('acha a aba pela URL, ignorando os filtros da query', () => {
    expect(indiceDaAba('/hoje')).toBe(0);
    expect(indiceDaAba('/tarefas?filtro=ATRASADAS')).toBe(3);
  });

  it('telas fora da barra nao sao abas', () => {
    expect(indiceDaAba('/rotina')).toBe(-1);
    expect(indiceDaAba('/tarefas/nova')).toBe(-1);
  });

  it('dentro de uma tarefa a secao continua sendo Tarefas', () => {
    expect(indiceDaSecao('/tarefas/15?origem=%2Ftarefas')).toBe(3);
    expect(indiceDaSecao('/tarefas/nova')).toBe(3);
    expect(indiceDaSecao('/rotina')).toBe(-1);
    expect(indiceDaSecao('/hojex')).toBe(-1);
  });

  it('a vizinha para nas pontas em vez de dar a volta', () => {
    expect(abaVizinha('/hoje', 1)?.rota).toBe('/resumo');
    expect(abaVizinha('/resumo', -1)?.rota).toBe('/hoje');
    expect(abaVizinha('/hoje', -1)).toBeNull();
    expect(abaVizinha('/tarefas', 1)).toBeNull();
    expect(abaVizinha('/ajustes', 1)).toBeNull();
  });
});

describe('rota das abas', () => {
  const casar = (...caminho: string[]) =>
    casarAba(caminho.map((p) => new UrlSegment(p, {})), null!, null!);

  it('as quatro abas caem na mesma rota', () => {
    expect(casar('hoje')?.consumed.map((s) => s.path)).toEqual(['hoje']);
    expect(casar('tarefas')).not.toBeNull();
  });

  it('o resto segue para as rotas de sempre', () => {
    expect(casar('rotina')).toBeNull();
    expect(casar('tarefas', 'nova')).toBeNull();
    expect(casar('tarefas', '15')).toBeNull();
  });
});

describe('gestos', () => {
  it('decide o eixo cedo, antes de o iPhone comecar a rolar', () => {
    expect(decidirEixo(4, 3)).toBeNull();
    expect(decidirEixo(7, 2)).toBe('horizontal');
  });

  it('ate 45 graus de inclinacao ainda e deslize de lado', () => {
    expect(decidirEixo(40, 5)).toBe('horizontal');
    expect(decidirEixo(20, 18)).toBe('horizontal');
    expect(decidirEixo(18, 20)).toBe('vertical');
    expect(decidirEixo(3, 30)).toBe('vertical');
  });

  it('troca depois de um quinto da tela ou num peteleco', () => {
    expect(deveTrocar(-80, 375, 0.1)).toBe(true);
    expect(deveTrocar(-60, 375, 0.1)).toBe(false);
    expect(deveTrocar(-30, 375, 0.35)).toBe(true);
  });

  it('um tremor rapido nao conta como peteleco', () => {
    expect(deveTrocar(-12, 375, 0.9)).toBe(false);
  });

  it('a velocidade e a dos ultimos 100 ms, nao a do ultimo movimento', () => {
    // O dedo correu e quase parou ao levantar: o ultimo trecho sozinho
    // daria 0,125 px/ms, e o peteleco nao contaria.
    const amostras = [
      { t: 0, x: 300 },
      { t: 50, x: 240 },
      { t: 100, x: 182 },
      { t: 116, x: 180 },
    ];
    expect(velocidadeRecente(amostras, 116)).toBeLessThan(-0.5);
  });

  it('sem dois pontos recentes, o dedo esta parado', () => {
    expect(velocidadeRecente([{ t: 0, x: 300 }], 500)).toBe(0);
  });

  it('a posicao na barra vira indice de aba, preso nas pontas', () => {
    // barra de 320px com 4 abas: cada uma tem 80px, centros em 40, 120, 200, 280
    expect(indicePelaPosicao(40, 320, 4)).toBe(0);
    expect(indicePelaPosicao(200, 320, 4)).toBe(2);
    expect(indicePelaPosicao(160, 320, 4)).toBe(1.5);
    expect(indicePelaPosicao(0, 320, 4)).toBe(0);
    expect(indicePelaPosicao(320, 320, 4)).toBe(3);
  });
});
