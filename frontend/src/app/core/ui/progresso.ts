/** Acima disto os segmentos ficariam finos demais para serem lidos. */
const MAXIMO_SEGMENTOS = 12;

/**
 * Um segmento por item, cheio ate onde o progresso chegou.
 *
 * Nulo quando sao itens demais: ai a barra continua volta a ler melhor, e a
 * tela troca de forma sozinha.
 */
export function segmentosDe(feitos: number, total: number): boolean[] | null {
  if (total === 0 || total > MAXIMO_SEGMENTOS) {
    return null;
  }
  return Array.from({ length: total }, (_, i) => i < feitos);
}
