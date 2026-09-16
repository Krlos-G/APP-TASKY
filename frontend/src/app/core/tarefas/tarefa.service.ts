import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FiltroTarefa, Tarefa, TarefaRequest } from './tarefa.models';

@Injectable({ providedIn: 'root' })
export class TarefaService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.urlApi}/tarefas`;

  listar(filtro: FiltroTarefa): Observable<Tarefa[]> {
    return this.http.get<Tarefa[]>(this.api, { params: new HttpParams().set('filtro', filtro) });
  }

  buscar(id: number): Observable<Tarefa> {
    return this.http.get<Tarefa>(`${this.api}/${id}`);
  }

  criar(pedido: TarefaRequest): Observable<Tarefa> {
    return this.http.post<Tarefa>(this.api, pedido);
  }

  atualizar(id: number, pedido: TarefaRequest): Observable<Tarefa> {
    return this.http.put<Tarefa>(`${this.api}/${id}`, pedido);
  }

  apagar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }

  concluir(id: number): Observable<Tarefa> {
    return this.http.put<Tarefa>(`${this.api}/${id}/conclusao`, {});
  }

  desfazerConclusao(id: number): Observable<Tarefa> {
    return this.http.delete<Tarefa>(`${this.api}/${id}/conclusao`);
  }
}
