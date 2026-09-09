import { DiaSemana } from '../rotina/rotina.models';

/** VEZES_POR_SEMANA existe no enum do servidor, mas ainda é recusado por ele. */
export type TipoAgenda = 'DIARIO' | 'DIAS_SEMANA' | 'VEZES_POR_SEMANA';

export type StatusHabito = 'FEITO' | 'PULADO';

export interface Habito {
  id: number;
  nome: string;
  icone: string | null;
  cor: string | null;
  tipoAgenda: TipoAgenda;
  diasSemana: DiaSemana[];
  horaPreferida: string | null;
  horaLembrete: string | null;
  arquivado: boolean;
  streak: number;
  /** Nulo é "ainda não marcado", diferente de PULADO. */
  statusHoje: StatusHabito | null;
  devidoHoje: boolean;
}

export interface HabitoRequest {
  nome: string;
  icone?: string | null;
  cor?: string | null;
  tipoAgenda: TipoAgenda;
  diasSemana?: DiaSemana[];
  horaPreferida?: string | null;
  horaLembrete?: string | null;
}

export interface Marcacao {
  data: string;
  status: StatusHabito;
}

/** O hábito como a tela do dia o recebe: status daquela data, streak de hoje. */
export interface HabitoDoDia {
  id: number;
  nome: string;
  icone: string | null;
  cor: string | null;
  horaPreferida: string | null;
  status: StatusHabito | null;
  streak: number;
}
