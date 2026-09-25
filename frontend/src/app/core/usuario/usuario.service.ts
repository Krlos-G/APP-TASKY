import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);

  definirFuso(fusoHorario: string): Observable<void> {
    return this.http.put<void>(`${environment.urlApi}/usuarios/eu/fuso`, { fusoHorario });
  }
}
