import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Dia } from './rotina.models';

@Injectable({ providedIn: 'root' })
export class DiaService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.urlApi}/dia`;

  /**
   * Sem data, o servidor usa hoje no fuso da conta.
   *
   * Deixar isso com o servidor evita divergencia quando o relogio do aparelho
   * esta em outro fuso - o caso do PC de viagem, ou do celular que nao ajustou.
   */
  buscar(data?: string): Observable<Dia> {
    const params = data ? new HttpParams().set('data', data) : undefined;
    return this.http.get<Dia>(this.api, { params });
  }
}
