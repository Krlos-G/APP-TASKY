import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { RespostaToken } from './auth.models';

/**
 * Payload JWT falso, so para o servico conseguir extrair nome e e-mail.
 * A assinatura nao importa: o cliente nunca a valida - quem valida e o backend.
 */
function tokenFalso(id: number, email: string, nome: string): string {
  const payload = btoa(JSON.stringify({ sub: String(id), email, nome }));
  return `cabecalho.${payload}.assinatura`;
}

function resposta(token: string): RespostaToken {
  return { accessToken: token, expiraEm: '2026-12-31T23:59:59Z' };
}

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('guarda o token e o usuario apos o login', () => {
    service.entrar({ email: 'carlos@tasky.app', senha: 'senha-longa' }).subscribe();

    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush(resposta(tokenFalso(1, 'carlos@tasky.app', 'Carlos')));

    expect(service.accessToken).toBeTruthy();
    expect(service.autenticado()).toBe(true);
    expect(service.usuario()).toEqual({ id: 1, email: 'carlos@tasky.app', nome: 'Carlos' });
  });

  it('envia o header de cliente no refresh', () => {
    service.renovar().subscribe();

    const req = http.expectOne('/api/v1/auth/refresh');
    // Sem este header o backend responde 403: e a defesa de CSRF do endpoint.
    expect(req.request.headers.get('X-Tasky-Client')).toBe('web');
    req.flush(resposta(tokenFalso(1, 'a@b.com', 'A')));
  });

  describe('renovacao concorrente', () => {
    it('varias chamadas simultaneas disparam UMA unica requisicao', () => {
      const recebidos: string[] = [];

      // Cinco interessados ao mesmo tempo, como cinco requisicoes tomando 401.
      for (let i = 0; i < 5; i++) {
        service.renovar().subscribe((token) => recebidos.push(token));
      }

      // Se houvesse mais de uma, o backend veria um refresh token ja usado e
      // trataria como replay, revogando a familia e derrubando a sessao.
      const requisicoes = http.match('/api/v1/auth/refresh');
      expect(requisicoes.length).toBe(1);

      requisicoes[0].flush(resposta(tokenFalso(1, 'a@b.com', 'A')));

      expect(recebidos.length).toBe(5);
      expect(new Set(recebidos).size).toBe(1);
    });

    it('depois de concluir, uma nova renovacao dispara requisicao nova', () => {
      service.renovar().subscribe();
      http.expectOne('/api/v1/auth/refresh').flush(resposta(tokenFalso(1, 'a@b.com', 'A')));

      service.renovar().subscribe();
      http.expectOne('/api/v1/auth/refresh').flush(resposta(tokenFalso(1, 'a@b.com', 'A')));
    });

    it('quando a renovacao falha, todos os interessados recebem o erro', () => {
      const erros: unknown[] = [];
      for (let i = 0; i < 3; i++) {
        service.renovar().subscribe({ error: (e) => erros.push(e) });
      }

      http.expectOne('/api/v1/auth/refresh')
        .flush({ erro: 'Sessao expirada.' }, { status: 401, statusText: 'Unauthorized' });

      expect(erros.length).toBe(3);
      expect(service.autenticado()).toBe(false);
    });

    it('libera para tentar de novo depois de uma falha', () => {
      service.renovar().subscribe({ error: () => undefined });
      http.expectOne('/api/v1/auth/refresh')
        .flush({}, { status: 401, statusText: 'Unauthorized' });

      service.renovar().subscribe({ error: () => undefined });
      http.expectOne('/api/v1/auth/refresh')
        .flush({}, { status: 401, statusText: 'Unauthorized' });
    });
  });

  it('restaurarSessao nunca rejeita, mesmo sem sessao', async () => {
    const promessa = service.restaurarSessao();

    http.expectOne('/api/v1/auth/refresh')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    await promessa; // resolve mesmo sem sessao: nunca rejeita
    expect(service.autenticado()).toBe(false);
  });

  it('sair limpa a sessao local', () => {
    service.entrar({ email: 'a@b.com', senha: 'senha-longa' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(resposta(tokenFalso(1, 'a@b.com', 'A')));
    expect(service.autenticado()).toBe(true);

    service.sair().subscribe();
    http.expectOne('/api/v1/auth/logout').flush(null);

    expect(service.autenticado()).toBe(false);
    expect(service.accessToken).toBeNull();
  });

  it('token malformado nao derruba o servico', () => {
    service.entrar({ email: 'a@b.com', senha: 'senha-longa' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(resposta('isto-nao-e-um-jwt'));

    expect(service.usuario()).toBeNull();
  });
});
