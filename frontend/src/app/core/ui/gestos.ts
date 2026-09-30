export type Eixo = 'horizontal' | 'vertical';

/** Movimento mínimo antes de decidir para que lado o dedo está indo. */
const LIMIAR_DECISAO = 10;

/**
 * O eixo só é decidido depois de um pouco de movimento, e o lado precisa
 * vencer com folga: arrasto meio diagonal é rolagem, não troca de tela.
 */
export function decidirEixo(dx: number, dy: number): Eixo | null {
  if (Math.abs(dx) < LIMIAR_DECISAO && Math.abs(dy) < LIMIAR_DECISAO) {
    return null;
  }
  return Math.abs(dx) > Math.abs(dy) * 1.2 ? 'horizontal' : 'vertical';
}

/**
 * Troca se o dedo passou de um quarto da tela, ou num peteleco rápido - que
 * é curto, mas ainda precisa de um mínimo para não confundir com um tremor.
 *
 * @param velocidade em px/ms
 */
export function deveTrocar(dx: number, largura: number, velocidade: number): boolean {
  const longe = Math.abs(dx) > largura * 0.25;
  const peteleco = Math.abs(velocidade) > 0.5 && Math.abs(dx) > 30;
  return longe || peteleco;
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
