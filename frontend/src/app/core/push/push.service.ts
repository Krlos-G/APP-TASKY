import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChavePush {
  /** False quando o servidor não tem chaves VAPID: a tela esconde a opção. */
  habilitado: boolean;
  chavePublica: string;
}

export interface InscricaoPushRequest {
  endpoint: string;
  p256dh: string;
  auth: string;
  userAgent: string;
}

export interface TesteEnvio {
  enviadas: number;
  aparelhos: number;
}

@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.urlApi}/inscricoes-push`;

  chave(): Observable<ChavePush> {
    return this.http.get<ChavePush>(`${this.api}/chave`);
  }

  registrar(inscricao: InscricaoPushRequest): Observable<void> {
    return this.http.post<void>(this.api, inscricao);
  }

  remover(endpoint: string): Observable<void> {
    return this.http.delete<void>(this.api, {
      params: new HttpParams().set('endpoint', endpoint),
    });
  }

  testar(): Observable<TesteEnvio> {
    return this.http.post<TesteEnvio>(`${this.api}/testar`, {});
  }
}
