import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protege as rotas internas.
 *
 * Nao tenta renovar a sessao aqui: quando o guard roda, a restauracao da
 * inicializacao ja terminou. Se nao ha usuario, e porque nao ha sessao mesmo.
 */
export const autenticacaoGuard: CanActivateFn = (_rota, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.autenticado()) {
    return true;
  }

  // Guarda o destino para voltar a ele depois do login.
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: estado.url },
  });
};

/** Impede que quem ja esta logado veja a tela de login de novo. */
export const visitanteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.autenticado() ? router.createUrlTree(['/hoje']) : true;
};
