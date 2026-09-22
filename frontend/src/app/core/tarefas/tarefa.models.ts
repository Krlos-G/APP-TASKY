export type FiltroTarefa = 'HOJE' | 'PROXIMAS' | 'ATRASADAS' | 'SEM_DATA' | 'CONCLUIDAS';

export const FILTROS_TAREFA: readonly { valor: FiltroTarefa; rotulo: string; vazio: string }[] = [
  { valor: 'HOJE', rotulo: 'Hoje', vazio: 'Nada planejado para hoje.' },
  { valor: 'PROXIMAS', rotulo: 'Próximas', vazio: 'Nada planejado para os próximos dias.' },
  { valor: 'ATRASADAS', rotulo: 'Atrasadas', vazio: 'Nenhuma tarefa atrasada.' },
  { valor: 'SEM_DATA', rotulo: 'Sem data', vazio: 'Nenhuma tarefa sem data.' },
  { valor: 'CONCLUIDAS', rotulo: 'Concluídas', vazio: 'Nenhuma tarefa concluída ainda.' },
];

export type Prioridade = 'BAIXA' | 'MEDIA' | 'ALTA';

export type StatusTarefa = 'A_FAZER' | 'FEITA' | 'CANCELADA';

export interface Tarefa {
  id: number;
  titulo: string;
  observacoes: string | null;
  prioridade: Prioridade;
  minutosEstimados: number | null;
  dataLimite: string | null;
  dataPlanejada: string | null;
  horaPlanejada: string | null;
  horaLembrete: string | null;
  status: StatusTarefa;
  concluidoEm: string | null;
  /** Calculados no servidor: dependem de "hoje" no fuso da conta. */
  atrasada: boolean;
  vencida: boolean;
}

export interface TarefaRequest {
  titulo: string;
  observacoes?: string | null;
  prioridade?: Prioridade;
  minutosEstimados?: number | null;
  dataLimite?: string | null;
  dataPlanejada?: string | null;
  horaPlanejada?: string | null;
  horaLembrete?: string | null;
}
