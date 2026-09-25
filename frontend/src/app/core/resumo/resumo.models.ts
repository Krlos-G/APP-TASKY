export interface ProgressoDoDia {
  habitosFeitos: number;
  habitosDevidos: number;
  tarefasFeitas: number;
  tarefasDoDia: number;
  atrasadas: number;
}

export interface ProgressoDaSemana {
  /** Segunda-feira; o fim é hoje, não domingo. */
  inicio: string;
  fim: string;
  habitosFeitos: number;
  habitosCobrados: number;
  tarefasConcluidas: number;
}

export interface Sequencia {
  id: number;
  nome: string;
  cor: string | null;
  streak: number;
}

export interface Resumo {
  data: string;
  dia: ProgressoDoDia;
  semana: ProgressoDaSemana;
  streaks: Sequencia[];
}
