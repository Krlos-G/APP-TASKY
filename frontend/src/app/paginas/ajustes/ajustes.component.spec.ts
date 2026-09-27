import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { of } from 'rxjs';
import { Ajustes } from './ajustes.component';
import { Perfil } from '../../core/usuario/usuario.service';

const PERFIL: Perfil = {
  email: 'carlos@tasky.app',
  nomeExibicao: 'Carlos',
  fusoHorario: 'America/Sao_Paulo',
  horaResumoDiario: null,
};

/** A inscrição que o navegador entregaria. */
function inscricaoFalsa(endpoint = 'https://web.push.apple.com/abc') {
  return {
    endpoint,
    toJSON: () => ({ keys: { p256dh: 'chave-do-navegador', auth: 'segredo' } }),
  } as unknown as PushSubscription;
}

describe('Ajustes', () => {
  let http: HttpTestingController;

  function configurar(swPush: Partial<SwPush>) {
    TestBed.configureTestingModule({
      imports: [Ajustes],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SwPush, useValue: swPush },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  function montar(opcoes: { habilitado?: boolean; perfil?: Perfil } = {}) {
    const fixture = TestBed.createComponent(Ajustes);
    fixture.detectChanges();

    http.expectOne('/api/v1/usuarios/eu').flush(opcoes.perfil ?? PERFIL);
    http.expectOne('/api/v1/inscricoes-push/chave').flush({
      habilitado: opcoes.habilitado ?? true,
      chavePublica: 'CHAVE-PUBLICA',
    });
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof montar>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function clicar(fixture: ReturnType<typeof montar>, rotulo: string): void {
    const alvo = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === rotulo);
    if (!alvo) {
      throw new Error(`Botão "${rotulo}" não encontrado`);
    }
    alvo.click();
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('sem service worker, explica em vez de oferecer um botao morto', () => {
    configurar({ isEnabled: false, subscription: of(null) });
    const fixture = montar();

    expect(texto(fixture)).toContain('não expõe notificações');
    expect(
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).map((b) =>
        b.textContent?.trim(),
      ),
    ).not.toContain('Ativar notificações');
  });

  it('servidor sem chaves esconde a ativacao', () => {
    configurar({ isEnabled: true, subscription: of(null) });
    const fixture = montar({ habilitado: false });

    expect(texto(fixture)).toContain('sem chaves de notificação');
  });

  it('ativar inscreve o aparelho e manda as chaves para o servidor', async () => {
    const inscricao = inscricaoFalsa();
    configurar({
      isEnabled: true,
      subscription: of(null),
      requestSubscription: () => Promise.resolve(inscricao),
    });
    const fixture = montar();

    clicar(fixture, 'Ativar notificações');
    await fixture.whenStable();

    const req = http.expectOne('/api/v1/inscricoes-push');
    expect(req.request.body).toEqual({
      endpoint: 'https://web.push.apple.com/abc',
      p256dh: 'chave-do-navegador',
      auth: 'segredo',
      userAgent: navigator.userAgent,
    });
    req.flush(null);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Notificações ativadas neste aparelho.');
  });

  it('recusa da permissao vira aviso, nao silencio', async () => {
    configurar({
      isEnabled: true,
      subscription: of(null),
      requestSubscription: () => Promise.reject(new Error('negado')),
    });
    const fixture = montar();

    clicar(fixture, 'Ativar notificações');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Não foi possível ativar');
    http.expectNone('/api/v1/inscricoes-push');
  });

  it('ja inscrito, oferece desativar e enviar teste', () => {
    configurar({ isEnabled: true, subscription: of(inscricaoFalsa()) });
    const fixture = montar();

    expect(texto(fixture)).toContain('Este aparelho recebe os lembretes.');

    clicar(fixture, 'Enviar teste');
    http.expectOne('/api/v1/inscricoes-push/testar').flush({ enviadas: 2, aparelhos: 2 });
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Enviado para 2 de 2');
  });

  it('desativar avisa o servidor antes do navegador', () => {
    let desinscreveu = false;
    configurar({
      isEnabled: true,
      subscription: of(inscricaoFalsa()),
      unsubscribe: () => {
        desinscreveu = true;
        return Promise.resolve();
      },
    });
    const fixture = montar();

    clicar(fixture, 'Desativar neste aparelho');

    const req = http.expectOne(
      (r) => r.method === 'DELETE' && r.url === '/api/v1/inscricoes-push',
    );
    expect(req.request.params.get('endpoint')).toBe('https://web.push.apple.com/abc');
    req.flush(null);
    fixture.detectChanges();

    expect(desinscreveu).toBe(true);
    expect(texto(fixture)).toContain('Notificações desativadas');
  });

  it('o resumo diario liga com horario e desliga com nulo', () => {
    configurar({ isEnabled: true, subscription: of(null) });
    const fixture = montar();

    const hora = (fixture.nativeElement as HTMLElement).querySelector(
      'input[type="time"]',
    ) as HTMLInputElement;
    hora.value = '07:00';
    hora.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    http.expectOne('/api/v1/usuarios/eu/resumo-diario').flush(null);
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Resumo diário às 07:00');

    clicar(fixture, 'Desligar');
    const req = http.expectOne('/api/v1/usuarios/eu/resumo-diario');
    expect(req.request.body).toEqual({ hora: null });
    req.flush(null);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Resumo diário desligado.');
  });

  it('mostra a conta e o fuso que veio do aparelho', () => {
    configurar({ isEnabled: true, subscription: of(null) });
    const fixture = montar();

    const conteudo = texto(fixture);
    expect(conteudo).toContain('carlos@tasky.app');
    expect(conteudo).toContain('America/Sao_Paulo');
    expect(conteudo).toContain('vem do aparelho');
  });
});
