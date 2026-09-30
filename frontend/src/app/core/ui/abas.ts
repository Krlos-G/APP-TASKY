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

/** A aba ao lado, na direção pedida - ou nula na ponta ou fora das abas. */
export function abaVizinha(url: string, passo: 1 | -1): Aba | null {
  const indice = indiceDaAba(url);
  return indice < 0 ? null : (ABAS[indice + passo] ?? null);
}

/**
 * De que lado a tela nova entra: pelo lado em que a aba dela fica na barra.
 * Nula quando uma das pontas não é aba - aí a tela só aparece.
 */
export function direcaoEntre(de: string, para: string): 'avanca' | 'volta' | null {
  const origem = indiceDaAba(de);
  const destino = indiceDaAba(para);
  if (origem < 0 || destino < 0 || origem === destino) {
    return null;
  }
  return destino > origem ? 'avanca' : 'volta';
}
