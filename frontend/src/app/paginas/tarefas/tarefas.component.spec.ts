import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Tarefas } from './tarefas.component';
import { Tarefa } from '../../core/tarefas/tarefa.models';
import { TimeProvider } from '../../core/tempo/time-provider.service';
import { AbaVisivel } from '../../core/ui/aba-visivel';
import { casarAba } from '../../core/ui/abas';

/** Quarta-feira. */
const HOJE = '2026-09-16';

function tarefa(parcial: Partial<Tarefa> = {}): Tarefa {
  return {
    id: 1,
    titulo: 'Responder e-mail',
    observacoes: null,
    prioridade: 'MEDIA',
    minutosEstimados: null,
    dataLimite: null,
    dataPlanejada: HOJE,
    horaPlanejada: null,
    horaLembrete: null,
    status: 'A_FAZER',
    concluidoEm: null,
    atrasada: false,
    vencida: false,
    ...parcial,
  };
}

describe('Tarefas', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        // A rota da trilha: ir para /hoje deixa a tela viva e escondida, como no app.
        provideRouter([{ matcher: casarAba, component: Tarefas }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TimeProvider, useValue: { hojeIso: () => HOJE } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function responder(pendentes: Tarefa[], concluidas: Tarefa[] = []): void {
    http
      .expectOne((r) => r.url === '/api/v1/tarefas' && r.params.get('filtro') === 'PENDENTES')
      .flush(pendentes);
    http
      .expectOne((r) => r.url === '/api/v1/tarefas' && r.params.get('filtro') === 'CONCLUIDAS')
      .flush(concluidas);
  }

  async function abrir(pendentes: Tarefa[], concluidas: Tarefa[] = []) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/tarefas');
    responder(pendentes, concluidas);
    harness.detectChanges();
    return harness;
  }

  function elemento(harness: RouterTestingHarness): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(harness: RouterTestingHarness): string {
    return elemento(harness).textContent ?? '';
  }

  function titulos(harness: RouterTestingHarness): string[] {
    return Array.from(elemento(harness).querySelectorAll('.secao__titulo')).map(
      (titulo) => titulo.textContent?.trim() ?? '',
    );
  }

  it('mostra as tarefas por quando, com as atrasadas em alerta', async () => {
    const harness = await abrir([
      tarefa({ id: 1, titulo: 'Velha', dataPlanejada: '2026-09-10', atrasada: true }),
      tarefa({ id: 2, titulo: 'De hoje', horaPlanejada: '14:30:00' }),
      tarefa({ id: 3, titulo: 'De amanha', dataPlanejada: '2026-09-17' }),
      tarefa({ id: 4, titulo: 'Algum dia', dataPlanejada: null }),
    ]);

    expect(titulos(harness)).toEqual(['Atrasadas', 'Hoje', 'Amanhã', 'Sem data']);
    expect(elemento(harness).querySelector('.secao__titulo--alerta')?.textContent).toContain(
      'Atrasadas',
    );
    // A atrasada diz de quando era; a de hoje, só o horário.
    expect(texto(harness)).toContain('de 10/09');
    expect(texto(harness)).toContain('14:30');
    expect(texto(harness)).not.toContain('16/09');
  });

  it('sem nada por fazer, a tela convida a criar a primeira', async () => {
    const harness = await abrir([]);

    expect(texto(harness)).toContain('Nada por fazer');
    expect(texto(harness)).toContain('Toque no + para anotar a primeira tarefa.');
  });

  it('concluidas ficam recolhidas no fim, com a contagem', async () => {
    const harness = await abrir(
      [tarefa()],
      [
        tarefa({ id: 8, titulo: 'Feita ontem', status: 'FEITA' }),
        tarefa({ id: 9, titulo: 'Feita antes', status: 'FEITA' }),
      ],
    );
    const alternar = elemento(harness).querySelector('.concluidas__alternar') as HTMLButtonElement;

    expect(alternar.textContent).toContain('Concluídas (2)');
    expect(alternar.getAttribute('aria-expanded')).toBe('false');
    expect(texto(harness)).not.toContain('Feita ontem');

    alternar.click();
    harness.detectChanges();

    expect(alternar.getAttribute('aria-expanded')).toBe('true');
    expect(texto(harness)).toContain('Feita ontem');
  });

  it('fechar recolhe as concluidas, e o que ficou escondido nao e alcancavel', async () => {
    const harness = await abrir([tarefa()], [tarefa({ id: 8, titulo: 'Feita', status: 'FEITA' })]);
    const alternar = elemento(harness).querySelector('.concluidas__alternar') as HTMLButtonElement;

    alternar.click();
    harness.detectChanges();
    const gaveta = elemento(harness).querySelector('.concluidas__gaveta') as HTMLElement;
    expect(gaveta.classList).toContain('concluidas__gaveta--aberta');
    expect(gaveta.hasAttribute('inert')).toBe(false);

    alternar.click();
    harness.detectChanges();

    // A lista fica, para a gaveta poder fechar deslizando - mas sem alcance.
    expect(alternar.getAttribute('aria-expanded')).toBe('false');
    expect(gaveta.classList).not.toContain('concluidas__gaveta--aberta');
    expect(gaveta.hasAttribute('inert')).toBe(true);
  });

  it('com tudo feito, avisa e deixa as concluidas logo abaixo', async () => {
    const harness = await abrir([], [tarefa({ status: 'FEITA' })]);

    expect(texto(harness)).toContain('Tudo em dia');
    expect(elemento(harness).querySelector('.concluidas__alternar')).not.toBeNull();
  });

  it('concluir deixa a tarefa no lugar, marcada', async () => {
    const harness = await abrir([tarefa()]);

    (elemento(harness).querySelector('.item__marca') as HTMLButtonElement).click();

    const req = http.expectOne('/api/v1/tarefas/1/conclusao');
    expect(req.request.method).toBe('PUT');
    req.flush(tarefa({ status: 'FEITA', concluidoEm: '2026-09-16T15:00:00Z' }));
    harness.detectChanges();

    const itens = elemento(harness).querySelectorAll('.item');
    expect(itens.length).toBe(1);
    expect(itens[0].classList).toContain('item--resolvido');
  });

  it('desfazer uma concluida envia o DELETE da conclusao', async () => {
    const harness = await abrir([], [tarefa({ status: 'FEITA' })]);
    (elemento(harness).querySelector('.concluidas__alternar') as HTMLButtonElement).click();
    harness.detectChanges();

    (elemento(harness).querySelector('.item__marca') as HTMLButtonElement).click();

    const req = http.expectOne('/api/v1/tarefas/1/conclusao');
    expect(req.request.method).toBe('DELETE');
    req.flush(tarefa());
  });

  it('tarefa vencida ganha o selo; prazo futuro aparece no detalhe', async () => {
    const harness = await abrir([
      tarefa({ id: 1, titulo: 'Vencida', dataLimite: '2026-09-15', vencida: true }),
      tarefa({ id: 2, titulo: 'No prazo', dataLimite: '2026-09-20', prioridade: 'ALTA' }),
    ]);

    expect(elemento(harness).querySelectorAll('.selo').length).toBe(1);
    expect(texto(harness)).toContain('prazo 20/09');
    expect(texto(harness)).toContain('prioridade alta');
  });

  it('a edicao volta para a lista', async () => {
    const harness = await abrir([tarefa()]);

    const link = elemento(harness).querySelector('.item__corpo') as HTMLAnchorElement;
    expect(decodeURIComponent(link.getAttribute('href') ?? '')).toBe('/tarefas/1?origem=/tarefas');
  });

  it('ao voltar a ficar visivel, atualiza sem esqueleto', async () => {
    const harness = await abrir([tarefa()]);

    TestBed.inject(AbaVisivel).avisar('/tarefas');
    harness.detectChanges();
    expect(elemento(harness).querySelector('app-esqueleto')).toBeNull();

    responder([tarefa({ titulo: 'Pagar a conta' })]);
    harness.detectChanges();

    expect(texto(harness)).toContain('Pagar a conta');
  });

  it('nascendo escondida na trilha, ja carrega a lista', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/hoje');

    responder([]);
  });

  it('trocar de aba nao busca de novo: quem atualiza e o aviso de visivel', async () => {
    const harness = await abrir([tarefa()]);

    await harness.navigateByUrl('/hoje');

    http.expectNone((r) => r.url === '/api/v1/tarefas');
  });
});
