import { abaVizinha, direcaoEntre, indiceDaAba } from './abas';
import { decidirEixo, deveTrocar, indicePelaPosicao } from './gestos';

describe('abas', () => {
  it('acha a aba pela URL, ignorando os filtros da query', () => {
    expect(indiceDaAba('/hoje')).toBe(0);
    expect(indiceDaAba('/tarefas?filtro=ATRASADAS')).toBe(3);
  });

  it('telas fora da barra nao sao abas', () => {
    expect(indiceDaAba('/rotina')).toBe(-1);
    expect(indiceDaAba('/tarefas/nova')).toBe(-1);
  });

  it('a vizinha para nas pontas em vez de dar a volta', () => {
    expect(abaVizinha('/hoje', 1)?.rota).toBe('/resumo');
    expect(abaVizinha('/resumo', -1)?.rota).toBe('/hoje');
    expect(abaVizinha('/hoje', -1)).toBeNull();
    expect(abaVizinha('/tarefas', 1)).toBeNull();
    expect(abaVizinha('/ajustes', 1)).toBeNull();
  });

  it('a tela nova entra pelo lado da aba dela na barra', () => {
    expect(direcaoEntre('/hoje', '/habitos')).toBe('avanca');
    expect(direcaoEntre('/tarefas?filtro=HOJE', '/resumo')).toBe('volta');
  });

  it('fora das abas, ou na mesma aba, nao ha lado', () => {
    expect(direcaoEntre('/hoje', '/rotina')).toBeNull();
    expect(direcaoEntre('/tarefas?filtro=HOJE', '/tarefas?filtro=ATRASADAS')).toBeNull();
  });
});

describe('gestos', () => {
  it('nao decide o eixo antes de o dedo andar um pouco', () => {
    expect(decidirEixo(4, 3)).toBeNull();
  });

  it('arrasto meio diagonal e rolagem, nao troca de tela', () => {
    expect(decidirEixo(40, 5)).toBe('horizontal');
    expect(decidirEixo(20, 18)).toBe('vertical');
    expect(decidirEixo(3, 30)).toBe('vertical');
  });

  it('troca depois de um quarto da tela ou num peteleco', () => {
    expect(deveTrocar(-120, 375, 0.1)).toBe(true);
    expect(deveTrocar(-60, 375, 0.1)).toBe(false);
    expect(deveTrocar(-45, 375, 0.8)).toBe(true);
  });

  it('um tremor rapido nao conta como peteleco', () => {
    expect(deveTrocar(-12, 375, 0.9)).toBe(false);
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
