import { HabitoDoDia } from '../habitos/habito.models';

export type DiaSemana = 'SEG' | 'TER' | 'QUA' | 'QUI' | 'SEX' | 'SAB' | 'DOM';

export const DIAS_SEMANA: readonly DiaSemana[] = [
  'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SAB', 'DOM',
] as const;

export const NOME_DO_DIA: Record<DiaSemana, string> = {
  SEG: 'Segunda',
  TER: 'Terça',
  QUA: 'Quarta',
  QUI: 'Quinta',
  SEX: 'Sexta',
  SAB: 'Sábado',
  DOM: 'Domingo',
};

/** Forma curta, para listas onde o nome inteiro não cabe. */
export const NOME_CURTO_DO_DIA: Record<DiaSemana, string> = {
  SEG: 'Seg',
  TER: 'Ter',
  QUA: 'Qua',
  QUI: 'Qui',
  SEX: 'Sex',
  SAB: 'Sáb',
  DOM: 'Dom',
};

export interface Bloco {
  id: number;
  titulo: string;
  /** Formato HH:mm, como o <input type="time"> produz. */
  horaInicio: string;
  horaFim: string;
  cor: string | null;
  minutosAntecedenciaLembrete: number | null;
}

export interface BlocoRequest {
  titulo: string;
  horaInicio: string;
  horaFim: string;
  cor?: string | null;
  minutosAntecedenciaLembrete?: number | null;
}

/** Aviso, não erro: sobrepor às vezes é intencional. */
export interface Sobreposicao {
  primeiroBlocoId: number;
  segundoBlocoId: number;
  descricao: string;
}

export interface ModeloDia {
  id: number;
  nome: string;
  padrao: boolean;
  blocos: Bloco[];
  sobreposicoes: Sobreposicao[];
}

export interface ModeloDiaRequest {
  nome: string;
  padrao: boolean;
}

export interface ModeloResumido {
  id: number;
  nome: string;
}

/** A API devolve sempre os sete dias; null onde não há rotina. */
export interface Semana {
  modeloPorDia: Record<DiaSemana, ModeloResumido | null>;
}

export interface Dia {
  data: string;
  diaSemana: DiaSemana;
  temRotina: boolean;
  nomeDoModelo: string | null;
  blocos: Bloco[];
  habitos: HabitoDoDia[];
  tarefas: unknown[];
}
