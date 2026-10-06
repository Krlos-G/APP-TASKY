import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Tarefas } from './tarefas.component';
import { AbaVisivel } from '../../core/ui/aba-visivel';
import { casarAba } from '../../core/ui/abas';
import { Tarefa } from '../../core/tarefas/tarefa.models';

function tarefa(parcial: Partial<Tarefa> = {}): Tarefa {
  return {
    id: 1,
    titulo: 'Responder e-mail',
    observacoes: null,
    prioridade: 'MEDIA',
    minutosEstimados: null,
    dataLimite: null,
    dataPlanejada: '2026-09-16',
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
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function abrir(url: string, filtroEsperado: string, lista: Tarefa[]) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    http
      .expectOne((r) => r.url === '/api/v1/tarefas' && r.params.get('filtro') === filtroEsperado)
      .flush(lista);
    harness.detectChanges();
    return harness;
  }

  function texto(harness: RouterTestingHarness): string {
    return harness.routeNativeElement?.textContent ?? '';
  }

  function elemento(harness: RouterTestingHarness): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  it('nascendo escondida na trilha, ja carrega o filtro padrao', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/hoje');

    http
      .expectOne((r) => r.url === '/api/v1/tarefas' && r.params.get('filtro') === 'HOJE')
      .flush([]);
  });

  it('viva e escondida na trilha, nao reage a URL das outras abas', async () => {
    const harness = await abrir('/tarefas?filtro=ATRASADAS', 'ATRASADAS', []);

    await harness.navigateByUrl('/hoje');

    http.expectNone((r) => r.url === '/api/v1/tarefas');
  });

  it('ao voltar a ficar visivel, atualiza o mesmo filtro sem esqueleto', async () => {
    const harness = await abrir('/tarefas?filtro=ATRASADAS', 'ATRASADAS', [tarefa()]);

    TestBed.inject(AbaVisivel).avisar('/tarefas');
    harness.detectChanges();
    expect(elemento(harness).querySelector('app-esqueleto')).toBeNull();

    http
      .expectOne((r) => r.url === '/api/v1/tarefas' && r.params.get('filtro') === 'ATRASADAS')
      .flush([tarefa({ titulo: 'Pagar a conta' })]);
    harness.detectChanges();

    expect(texto(harness)).toContain('Pagar a conta');
  });

  it('sem filtro na URL, abre na aba Hoje', async () => {
    const harness = await abrir('/tarefas', 'HOJE', []);

    expect(texto(harness)).toContain('Nada planejado para hoje.');
    expect(elemento(harness).querySelector('.abas__item--ativa')?.textContent).toContain('Hoje');
  });

  it('o filtro vem da URL', async () => {
    const harness = await abrir('/tarefas?filtro=ATRASADAS', 'ATRASADAS', [
      tarefa({ dataPlanejada: '2026-09-15', horaPlanejada: '09:00:00', atrasada: true }),
    ]);

    expect(elemento(harness).querySelector('.abas__item--ativa')?.textContent).toContain(
      'Atrasadas',
    );
    // Fora da aba Hoje, a data precisa aparecer.
    expect(texto(harness)).toContain('15/09 09:00');
  });

  it('filtro desconhecido na URL cai em Hoje em vez de ir ao servidor', async () => {
    await abrir('/tarefas?filtro=TODAS', 'HOJE', []);
  });

  it('na aba Hoje mostra so o horario, sem repetir a data', async () => {
    const harness = await abrir('/tarefas', 'HOJE', [tarefa({ horaPlanejada: '14:30:00' })]);

    expect(texto(harness)).toContain('14:30');
    expect(texto(harness)).not.toContain('16/09');
  });

  it('concluir troca a tarefa no lugar, sem tira-la da lista', async () => {
    const harness = await abrir('/tarefas?filtro=ATRASADAS', 'ATRASADAS', [tarefa()]);

    (elemento(harness).querySelector('.item__marca') as HTMLButtonElement).click();

    const req = http.expectOne('/api/v1/tarefas/1/conclusao');
    expect(req.request.method).toBe('PUT');
    req.flush(tarefa({ status: 'FEITA', concluidoEm: '2026-09-16T15:00:00Z' }));
    harness.detectChanges();

    const itens = elemento(harness).querySelectorAll('.item');
    expect(itens.length).toBe(1);
    expect(itens[0].classList).toContain('item--resolvido');
  });

  it('desfazer envia o DELETE da conclusao', async () => {
    const harness = await abrir('/tarefas?filtro=CONCLUIDAS', 'CONCLUIDAS', [
      tarefa({ status: 'FEITA' }),
    ]);

    (elemento(harness).querySelector('.item__marca') as HTMLButtonElement).click();

    const req = http.expectOne('/api/v1/tarefas/1/conclusao');
    expect(req.request.method).toBe('DELETE');
    req.flush(tarefa());
  });

  it('tarefa vencida ganha o selo; prazo futuro aparece no detalhe', async () => {
    const harness = await abrir('/tarefas?filtro=PROXIMAS', 'PROXIMAS', [
      tarefa({ id: 1, titulo: 'Vencida', dataLimite: '2026-09-15', vencida: true }),
      tarefa({ id: 2, titulo: 'No prazo', dataLimite: '2026-09-20', prioridade: 'ALTA' }),
    ]);

    expect(elemento(harness).querySelectorAll('.selo').length).toBe(1);
    expect(texto(harness)).toContain('prazo 20/09');
    expect(texto(harness)).toContain('prioridade alta');
  });
});
