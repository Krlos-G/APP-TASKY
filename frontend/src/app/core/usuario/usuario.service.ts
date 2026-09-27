import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Perfil {
  email: string;
  nomeExibicao: string;
  fusoHorario: string;
  /** Nula = resumo diário desligado. */
  horaResumoDiario: string | null;
}

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.urlApi}/usuarios/eu`;

  perfil(): Observable<Perfil> {
    return this.http.get<Perfil>(this.api);
  }

  definirResumoDiario(hora: string | null): Observable<void> {
    return this.http.put<void>(`${this.api}/resumo-diario`, { hora });
  }

  definirFuso(fusoHorario: string): Observable<void> {
    return this.http.put<void>(`${this.api}/fuso`, { fusoHorario });
  }
}
