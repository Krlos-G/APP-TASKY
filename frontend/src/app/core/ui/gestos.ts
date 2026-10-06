export type Eixo = 'horizontal' | 'vertical';

/**
 * Movimento mínimo antes de decidir para que lado o dedo está indo. Menor que
 * a folga do iPhone antes de começar a rolar: decidido aqui, o app ainda pode
 * impedir a rolagem.
 */
const LIMIAR_DECISAO = 6;

/** Velocidade medida nesta janela final, e não só no último movimento. */
const JANELA_VELOCIDADE = 100;

export interface Amostra {
  /** Instante, em ms. */
  t: number;
  x: number;
}

/**
 * Até 45 graus de inclinação ainda é deslize de lado: o polegar descreve um
 * arco, e exigir um traço reto obrigava a fazer força para trocar de aba.
 */
export function decidirEixo(dx: number, dy: number): Eixo | null {
  if (Math.abs(dx) < LIMIAR_DECISAO && Math.abs(dy) < LIMIAR_DECISAO) {
    return null;
  }
  return Math.abs(dx) > Math.abs(dy) ? 'horizontal' : 'vertical';
}

/**
 * Troca se o dedo passou de um quinto da tela, ou num peteleco - que é curto,
 * mas ainda precisa de um mínimo para não confundir com um tremor.
 *
 * @param velocidade em px/ms
 */
export function deveTrocar(dx: number, largura: number, velocidade: number): boolean {
  const longe = Math.abs(dx) > largura * 0.2;
  const peteleco = Math.abs(velocidade) > 0.3 && Math.abs(dx) > 20;
  return longe || peteleco;
}

/**
 * Velocidade do dedo nos últimos 100 ms, em px/ms. O último trecho sozinho
 * mente: o dedo desacelera ao levantar, e o peteleco quase nunca contava.
 */
export function velocidadeRecente(amostras: readonly Amostra[], agora: number): number {
  const recentes = amostras.filter((amostra) => amostra.t >= agora - JANELA_VELOCIDADE);
  if (recentes.length < 2) {
    return 0;
  }
  const primeira = recentes[0];
  const ultima = recentes[recentes.length - 1];
  return (ultima.x - primeira.x) / Math.max(1, ultima.t - primeira.t);
}

/**
 * Onde o dedo está sobre a barra, em unidades de aba: 0 é o centro da primeira,
 * 1 o da segunda. Preso entre a primeira e a última, para a pílula não sair.
 *
 * @param x posição do dedo a partir da borda esquerda da barra
 */
export function indicePelaPosicao(x: number, largura: number, total: number): number {
  const bruto = (x / largura) * total - 0.5;
  return Math.min(total - 1, Math.max(0, bruto));
}
