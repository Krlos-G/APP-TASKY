import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { autenticacaoInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { RespostaToken } from './auth.models';

function tokenFalso(nome: string): string {
  const payload = btoa(JSON.stringify({ sub: '1', email: 'a@b.com', nome }));
  return `cabecalho.${payload}.assinatura`;
}

function resposta(token: string): RespostaToken {
  return { accessToken: token, expiraEm: '2026-12-31T23:59:59Z' };
}

describe('autenticacaoInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: AuthService;
  let router: { navigate: ReturnType<typeof vi.fn>; url: string };

  beforeEach(() => {
    router = { navigate: vi.fn(), url: '/hoje' };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([autenticacaoInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => controller.verify());

  /** Coloca o servico em estado autenticado, como depois de um login. */
  function autenticar(nome = 'Carlos'): void {
    auth.entrar({ email: 'a@b.com', senha: 'senha-longa' }).subscribe();
    controller.expectOne('/api/v1/auth/login').flush(resposta(tokenFalso(nome)));
  }

  it('anexa o Bearer quando ha token', () => {
    autenticar();

    http.get('/api/v1/ping').subscribe();

    const req = controller.expectOne('/api/v1/ping');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${auth.accessToken}`);
    req.flush({});
  });

  it('nao anexa Bearer nas rotas de autenticacao', () => {
    autenticar();

    http.post('/api/v1/auth/login', {}).subscribe();

    const req = controller.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(resposta(tokenFalso('Carlos')));
  });

  it('no 401 renova e repete a requisicao com o token novo', () => {
    autenticar();
    const tokenAntigo = auth.accessToken;
    let resultado: unknown = null;

    http.get('/api/v1/ping').subscribe((r) => (resultado = r));

    controller.expectOne('/api/v1/ping').flush({}, { status: 401, statusText: 'Unauthorized' });

    controller.expectOne('/api/v1/auth/refresh').flush(resposta(tokenFalso('Renovado')));

    const repetida = controller.expectOne('/api/v1/ping');
    expect(repetida.request.headers.get('Authorization')).not.toBe(`Bearer ${tokenAntigo}`);
    repetida.flush({ ok: true });

    expect(resultado).toEqual({ ok: true });
  });

  it('repete apenas UMA vez: 401 na repeticao nao vira laco', () => {
    autenticar();
    let erro: unknown = null;

    http.get('/api/v1/ping').subscribe({ error: (e) => (erro = e) });

    controller.expectOne('/api/v1/ping').flush({}, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/v1/auth/refresh').flush(resposta(tokenFalso('Renovado')));
    // A repeticao tambem falha; nao pode haver terceira tentativa.
    controller.expectOne('/api/v1/ping').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(erro).toBeTruthy();
    controller.expectNone('/api/v1/auth/refresh');
  });

  it('requisicoes concorrentes com 401 compartilham uma unica renovacao', () => {
    autenticar();

    http.get('/api/v1/a').subscribe({ error: () => undefined });
    http.get('/api/v1/b').subscribe({ error: () => undefined });
    http.get('/api/v1/c').subscribe({ error: () => undefined });

    controller.expectOne('/api/v1/a').flush({}, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/v1/b').flush({}, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/v1/c').flush({}, { status: 401, statusText: 'Unauthorized' });

    // O ponto central da etapa: tres 401 nao podem virar tres rotacoes, ou o
    // backend enxergaria replay e revogaria a familia inteira.
    const renovacoes = controller.match('/api/v1/auth/refresh');
    expect(renovacoes.length).toBe(1);
    renovacoes[0].flush(resposta(tokenFalso('Renovado')));

    // As tres seguem adiante com o token novo.
    expect(controller.match('/api/v1/a').length).toBe(1);
    expect(controller.match('/api/v1/b').length).toBe(1);
    expect(controller.match('/api/v1/c').length).toBe(1);
    controller.match(() => true).forEach((r) => r.flush({}));
  });

  it('quando a renovacao falha, limpa a sessao e manda para o login', () => {
    autenticar();

    http.get('/api/v1/ping').subscribe({ error: () => undefined });

    controller.expectOne('/api/v1/ping').flush({}, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/v1/auth/refresh')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.autenticado()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'],
      expect.objectContaining({ queryParams: { returnUrl: '/hoje' } }),
    );
  });

  it('401 sem token nunca tenta renovar', () => {
    http.get('/api/v1/ping').subscribe({ error: () => undefined });

    controller.expectOne('/api/v1/ping').flush({}, { status: 401, statusText: 'Unauthorized' });

    controller.expectNone('/api/v1/auth/refresh');
  });

  it('erros que nao sao 401 sobem sem renovar', () => {
    autenticar();
    let erro: unknown = null;

    http.get('/api/v1/ping').subscribe({ error: (e) => (erro = e) });
    controller.expectOne('/api/v1/ping')
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(erro).toBeTruthy();
    controller.expectNone('/api/v1/auth/refresh');
  });
});
