import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BlocoRequest,
  DiaSemana,
  ModeloDia,
  ModeloDiaRequest,
  Semana,
} from './rotina.models';

@Injectable({ providedIn: 'root' })
export class RotinaService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.urlApi}/rotina`;

  listarModelos(): Observable<ModeloDia[]> {
    return this.http.get<ModeloDia[]>(`${this.api}/modelos`);
  }

  criarModelo(pedido: ModeloDiaRequest): Observable<ModeloDia> {
    return this.http.post<ModeloDia>(`${this.api}/modelos`, pedido);
  }

  atualizarModelo(id: number, pedido: ModeloDiaRequest): Observable<ModeloDia> {
    return this.http.put<ModeloDia>(`${this.api}/modelos/${id}`, pedido);
  }

  apagarModelo(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/modelos/${id}`);
  }

  /**
   * As operacoes de bloco devolvem o modelo inteiro, nao so o bloco: os avisos
   * de sobreposicao sao recalculados a cada mudanca e a tela precisa deles.
   */
  adicionarBloco(modeloId: number, pedido: BlocoRequest): Observable<ModeloDia> {
    return this.http.post<ModeloDia>(`${this.api}/modelos/${modeloId}/blocos`, pedido);
  }

  atualizarBloco(blocoId: number, pedido: BlocoRequest): Observable<ModeloDia> {
    return this.http.put<ModeloDia>(`${this.api}/blocos/${blocoId}`, pedido);
  }

  apagarBloco(blocoId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/blocos/${blocoId}`);
  }

  verSemana(): Observable<Semana> {
    return this.http.get<Semana>(`${this.api}/semana`);
  }

  /** Substituicao do conjunto: manda os sete dias de uma vez. */
  definirSemana(modeloPorDia: Record<DiaSemana, number | null>): Observable<Semana> {
    return this.http.put<Semana>(`${this.api}/semana`, { modeloPorDia });
  }
}
