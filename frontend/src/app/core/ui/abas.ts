import { UrlMatcher } from '@angular/router';
import { NomeIcone } from './icone.component';

export interface Aba {
  rota: string;
  rotulo: string;
  icone: NomeIcone;
}

/**
 * As abas na ordem da barra. A mesma lista desenha a barra, decide para onde
 * o deslizar leva e de que lado a tela nova entra.
 */
export const ABAS: readonly Aba[] = [
  { rota: '/hoje', rotulo: 'Hoje', icone: 'hoje' },
  { rota: '/resumo', rotulo: 'Resumo', icone: 'resumo' },
  { rota: '/habitos', rotulo: 'Hábitos', icone: 'habitos' },
  { rota: '/tarefas', rotulo: 'Tarefas', icone: 'tarefas' },
];

/** Posição da aba na URL, ou -1 fora das abas (rotina, ajustes, edição). */
export function indiceDaAba(url: string): number {
  const caminho = url.split(/[?#]/)[0];
  return ABAS.findIndex((aba) => aba.rota === caminho);
}

/**
 * Uma rota só para as quatro abas: trocar de aba reaproveita a trilha, em vez
 * de destruir uma tela e criar outra.
 */
export const casarAba: UrlMatcher = (segmentos) =>
  segmentos.length === 1 && indiceDaAba('/' + segmentos[0].path) >= 0
    ? { consumed: segmentos }
    : null;

/**
 * A aba da seção: a edição de uma tarefa continua dentro de Tarefas, como um
 * detalhe aberto numa aba do iOS, que não tira a aba de selecionada.
 */
export function indiceDaSecao(url: string): number {
  const caminho = url.split(/[?#]/)[0];
  return ABAS.findIndex((aba) => caminho === aba.rota || caminho.startsWith(aba.rota + '/'));
}

/** A aba ao lado, na direção pedida - ou nula na ponta ou fora das abas. */
export function abaVizinha(url: string, passo: 1 | -1): Aba | null {
  const indice = indiceDaAba(url);
  return indice < 0 ? null : (ABAS[indice + passo] ?? null);
}
