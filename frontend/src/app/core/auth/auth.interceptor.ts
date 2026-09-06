import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Rotas que nunca levam Bearer nem disparam renovacao. */
const ROTAS_PUBLICAS = ['/auth/login', '/auth/registrar', '/auth/refresh'];

function ehRotaPublica(url: string): boolean {
  return ROTAS_PUBLICAS.some((rota) => url.includes(rota));
}

function comBearer<T>(requisicao: HttpRequest<T>, token: string): HttpRequest<T> {
  return requisicao.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Anexa o access token e renova a sessao quando ele expira.
 *
 * No 401, chama a renovacao e repete a requisicao original UMA vez. A
 * requisicao repetida vai direto para next(), sem reentrar no interceptor,
 * entao nao ha risco de laco infinito caso o novo token tambem seja recusado.
 */
export const autenticacaoInterceptor: HttpInterceptorFn = (requisicao, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (ehRotaPublica(requisicao.url)) {
    return next(requisicao);
  }

  const token = auth.accessToken;
  const comCredencial = token ? comBearer(requisicao, token) : requisicao;

  return next(comCredencial).pipe(
    catchError((erro: unknown) => {
      const naoAutorizado = erro instanceof HttpErrorResponse && erro.status === 401;

      // Sem token nao ha o que renovar: o 401 e legitimo e sobe.
      if (!naoAutorizado || !token) {
        return throwError(() => erro);
      }

      // Varias requisicoes falhando juntas compartilham a MESMA renovacao;
      // quem cuida disso e o AuthService.
      return auth.renovar().pipe(
        switchMap((novoToken) => next(comBearer(requisicao, novoToken))),
        catchError((erroDaRenovacao: unknown) => {
          auth.limparSessao();
          void router.navigate(['/login'], {
            queryParams: { returnUrl: router.url },
          });
          return throwError(() => erroDaRenovacao);
        }),
      );
    }),
  );
};
