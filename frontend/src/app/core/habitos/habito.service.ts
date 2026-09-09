import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Habito, HabitoRequest, Marcacao, StatusHabito } from './habito.models';

@Injectable({ providedIn: 'root' })
export class HabitoService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.urlApi}/habitos`;

  listar(incluirArquivados = false): Observable<Habito[]> {
    const params = incluirArquivados
      ? new HttpParams().set('incluirArquivados', 'true')
      : undefined;
    return this.http.get<Habito[]>(this.api, { params });
  }

  criar(pedido: HabitoRequest): Observable<Habito> {
    return this.http.post<Habito>(this.api, pedido);
  }

  atualizar(id: number, pedido: HabitoRequest): Observable<Habito> {
    return this.http.put<Habito>(`${this.api}/${id}`, pedido);
  }

  definirArquivo(id: number, arquivado: boolean): Observable<Habito> {
    return this.http.put<Habito>(`${this.api}/${id}/arquivo`, { arquivado });
  }

  /** Leva o histórico junto; arquivar é que preserva. */
  apagar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }

  /**
   * Marcar e desmarcar devolvem o hábito com o streak recalculado, então a tela
   * substitui o item pelo que voltou em vez de recarregar a lista.
   */
  marcar(id: number, data: string, status: StatusHabito): Observable<Habito> {
    return this.http.put<Habito>(`${this.api}/${id}/registros/${data}`, { status });
  }

  desmarcar(id: number, data: string): Observable<Habito> {
    return this.http.delete<Habito>(`${this.api}/${id}/registros/${data}`);
  }

  historico(id: number): Observable<Marcacao[]> {
    return this.http.get<Marcacao[]>(`${this.api}/${id}/historico`);
  }
}
