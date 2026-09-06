import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { autenticacaoGuard, visitanteGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { RespostaToken } from './auth.models';

function resposta(): RespostaToken {
  const payload = btoa(JSON.stringify({ sub: '1', email: 'a@b.com', nome: 'Carlos' }));
  return { accessToken: `cabecalho.${payload}.assinatura`, expiraEm: '2026-12-31T23:59:59Z' };
}

describe('guards de autenticacao', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  /** Autentica de verdade, em vez de simular o estado do servico. */
  function autenticar(): void {
    auth.entrar({ email: 'a@b.com', senha: 'senha-longa' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(resposta());
  }

  function rodar(guard: typeof autenticacaoGuard, url: string) {
    return TestBed.runInInjectionContext(() =>
      guard({} as never, { url } as RouterStateSnapshot),
    );
  }

  it('bloqueia quem nao esta autenticado e preserva o destino', () => {
    const resultado = rodar(autenticacaoGuard, '/tarefas');

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree))
      .toContain('returnUrl=%2Ftarefas');
  });

  it('libera quem esta autenticado', () => {
    autenticar();

    expect(rodar(autenticacaoGuard, '/hoje')).toBe(true);
  });

  it('tira de /login quem ja esta logado', () => {
    autenticar();

    expect(rodar(visitanteGuard, '/login')).toBeInstanceOf(UrlTree);
  });

  it('deixa visitante ver /login', () => {
    expect(rodar(visitanteGuard, '/login')).toBe(true);
  });
});
