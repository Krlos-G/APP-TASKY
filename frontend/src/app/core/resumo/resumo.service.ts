import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Resumo } from './resumo.models';

@Injectable({ providedIn: 'root' })
export class ResumoService {
  private readonly http = inject(HttpClient);

  ver(): Observable<Resumo> {
    return this.http.get<Resumo>(`${environment.urlApi}/resumo`);
  }
}
