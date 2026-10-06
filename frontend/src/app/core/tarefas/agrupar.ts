import { dataDeIso } from '../tempo/formatos';
import { Tarefa } from './tarefa.models';

export interface SecaoDeTarefas {
  chave: string;
  titulo: string;
  /** Atrasada pede atenção: o título sai em vermelho. */
  alerta: boolean;
  tarefas: Tarefa[];
}

/**
 * A lista de Tarefas por quando: atrasadas, hoje, amanhã, cada dia seguinte e
 * sem data. Seção sem tarefa não existe - a tela só fica vazia quando não há
 * nada por fazer. Dentro da seção fica a ordem que veio do servidor.
 *
 * @param hoje o dia do aparelho, em ISO: "hoje" e "amanhã" são dele
 */
export function agruparPorQuando(tarefas: readonly Tarefa[], hoje: string): SecaoDeTarefas[] {
  const amanha = somarDias(hoje, 1);
  const secoes = new Map<string, SecaoDeTarefas & { ordem: string }>();

  for (const tarefa of tarefas) {
    const [chave, titulo, ordem] = ondeFica(tarefa.dataPlanejada, hoje, amanha);
    let secao = secoes.get(chave);
    if (!secao) {
      secao = { chave, titulo, alerta: chave === 'atrasadas', tarefas: [], ordem };
      secoes.set(chave, secao);
    }
    secao.tarefas.push(tarefa);
  }

  return [...secoes.values()]
    .sort((a, b) => a.ordem.localeCompare(b.ordem))
    .map(({ ordem: _ordem, ...secao }) => secao);
}

/** @returns chave da seção, título e por onde ela entra na ordem da tela */
function ondeFica(dia: string | null, hoje: string, amanha: string): [string, string, string] {
  if (!dia) {
    return ['sem-data', 'Sem data', '9999-12-31'];
  }
  if (dia < hoje) {
    return ['atrasadas', 'Atrasadas', '0000-01-01'];
  }
  if (dia === hoje) {
    return ['hoje', 'Hoje', dia];
  }
  return [dia, dia === amanha ? 'Amanhã' : porExtenso(dia, hoje), dia];
}

/** "sexta, 9 de outubro" - e o ano, se não for este. */
function porExtenso(dia: string, hoje: string): string {
  const outroAno = dia.slice(0, 4) !== hoje.slice(0, 4);
  return dataDeIso(dia)
    .toLocaleDateString('pt-BR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: outroAno ? 'numeric' : undefined,
    })
    .replace('-feira', '');
}

function somarDias(iso: string, dias: number): string {
  const data = dataDeIso(iso);
  data.setDate(data.getDate() + dias);
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}
