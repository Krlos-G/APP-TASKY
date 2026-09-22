import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TarefaEdicao } from './tarefa-edicao.component';
import { Tarefa } from '../../core/tarefas/tarefa.models';

function tarefa(parcial: Partial<Tarefa> = {}): Tarefa {
  return {
    id: 7,
    titulo: 'Responder e-mail',
    observacoes: null,
    prioridade: 'MEDIA',
    minutosEstimados: null,
    dataLimite: null,
    dataPlanejada: null,
    horaPlanejada: null,
    horaLembrete: null,
    status: 'A_FAZER',
    concluidoEm: null,
    atrasada: false,
    vencida: false,
    ...parcial,
  };
}

describe('TarefaEdicao', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'tarefas/nova', component: TarefaEdicao },
          { path: 'tarefas/:id', component: TarefaEdicao },
          { path: 'tarefas', children: [] },
          { path: 'hoje', children: [] },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function abrir(url: string) {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.detectChanges();
  }

  function elemento(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function campo(nome: string): HTMLInputElement {
    return elemento().querySelector(`[formControlName="${nome}"]`) as HTMLInputElement;
  }

  function digitar(nome: string, valor: string): void {
    const entrada = campo(nome);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  function clicar(rotulo: string): void {
    const alvo = Array.from(elemento().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === rotulo,
    );
    if (!alvo) {
      throw new Error(`Botão "${rotulo}" não encontrado`);
    }
    (alvo as HTMLButtonElement).click();
    harness.detectChanges();
  }

  function urlAtual(): string {
    return TestBed.inject(Router).url;
  }

  /** O componente navega sozinho; o teste só espera a navegação assentar. */
  async function navegou(): Promise<string> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    return urlAtual();
  }

  it('cria a tarefa e volta para a origem', async () => {
    await abrir('/tarefas/nova?origem=%2Ftarefas%3Ffiltro%3DATRASADAS');

    expect(elemento().textContent).toContain('Nova tarefa');
    digitar('titulo', 'Pagar boleto');
    clicar('Salvar');

    const req = http.expectOne('/api/v1/tarefas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.titulo).toBe('Pagar boleto');
    expect(req.request.body.prioridade).toBe('MEDIA');
    req.flush(tarefa({ titulo: 'Pagar boleto' }));

    expect(await navegou()).toBe('/tarefas?filtro=ATRASADAS');
  });

  it('o dia vem preenchido quando a tela abre pelo Hoje', async () => {
    await abrir('/tarefas/nova?data=2026-09-21&origem=%2Fhoje');

    expect(campo('dataPlanejada').value).toBe('2026-09-21');
    // Com dia escolhido, o horário já está liberado.
    expect(campo('horaPlanejada').disabled).toBe(false);
  });

  it('o horario so abre depois do dia', async () => {
    await abrir('/tarefas/nova');

    expect(campo('horaPlanejada').disabled).toBe(true);
    expect(elemento().textContent).toContain('escolha o dia primeiro');

    digitar('dataPlanejada', '2026-09-21');
    expect(campo('horaPlanejada').disabled).toBe(false);

    digitar('horaPlanejada', '14:30');
    // Limpar o dia leva o horário junto: o servidor recusaria o par.
    digitar('dataPlanejada', '');
    expect(campo('horaPlanejada').disabled).toBe(true);
    expect(campo('horaPlanejada').value).toBe('');
  });

  it('titulo em branco nao chega a ser enviado', async () => {
    await abrir('/tarefas/nova');

    clicar('Salvar');

    http.expectNone((r) => r.method === 'POST');
  });

  it('editando, carrega a tarefa no formulario e salva com PUT', async () => {
    await abrir('/tarefas/7?origem=%2Ftarefas%3Ffiltro%3DHOJE');

    http.expectOne('/api/v1/tarefas/7').flush(
      tarefa({
        titulo: 'Revisar PR',
        prioridade: 'ALTA',
        minutosEstimados: 90,
        dataPlanejada: '2026-09-21',
        horaPlanejada: '19:00:00',
      }),
    );
    harness.detectChanges();

    expect(elemento().textContent).toContain('Editar tarefa');
    expect(campo('titulo').value).toBe('Revisar PR');
    expect(campo('minutosEstimados').value).toBe('90');
    expect(campo('horaPlanejada').value).toBe('19:00');

    digitar('titulo', 'Revisar PR do Tasky');
    clicar('Salvar');

    const req = http.expectOne('/api/v1/tarefas/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.titulo).toBe('Revisar PR do Tasky');
    expect(req.request.body.horaPlanejada).toBe('19:00');
    expect(req.request.body.minutosEstimados).toBe(90);
    req.flush(tarefa());
  });

  it('excluir pede confirmacao antes de apagar', async () => {
    await abrir('/tarefas/7?origem=%2Fhoje');
    http.expectOne('/api/v1/tarefas/7').flush(tarefa());
    harness.detectChanges();

    clicar('Excluir tarefa');
    expect(elemento().textContent).toContain('Não dá para desfazer');
    http.expectNone((r) => r.method === 'DELETE');

    clicar('Excluir mesmo assim');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/tarefas/7').flush(null);

    expect(await navegou()).toBe('/hoje');
  });

  it('cancelar volta sem enviar nada', async () => {
    await abrir('/tarefas/nova?origem=%2Fhoje');

    digitar('titulo', 'Desistir');
    clicar('Cancelar');

    expect(await navegou()).toBe('/hoje');
    http.expectNone((r) => r.method === 'POST');
  });

  it('origem para fora do app e ignorada', async () => {
    await abrir('/tarefas/nova?origem=%2F%2Foutro.site');

    digitar('titulo', 'Qualquer');
    clicar('Salvar');
    http.expectOne('/api/v1/tarefas').flush(tarefa());

    expect(await navegou()).toBe('/tarefas');
  });
});
