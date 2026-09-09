import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Habitos } from './habitos.component';
import { TimeProvider } from '../../core/tempo/time-provider.service';
import { Habito } from '../../core/habitos/habito.models';

const HOJE = '2026-09-07';

function habito(parcial: Partial<Habito> = {}): Habito {
  return {
    id: 1,
    nome: 'Ler',
    icone: null,
    cor: null,
    tipoAgenda: 'DIARIO',
    diasSemana: [],
    horaPreferida: null,
    horaLembrete: null,
    arquivado: false,
    streak: 0,
    statusHoje: null,
    devidoHoje: true,
    ...parcial,
  };
}

describe('Habitos', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    const relogioFixo = {
      agora: () => new Date(2026, 8, 7, 10, 0),
      minutosDoDia: () => 600,
      hojeIso: () => HOJE,
      parar: () => undefined,
    };

    TestBed.configureTestingModule({
      imports: [Habitos],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TimeProvider, useValue: relogioFixo },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function montar(lista: Habito[]) {
    const fixture = TestBed.createComponent(Habitos);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/habitos') && r.method === 'GET').flush(lista);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof montar>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function botao(fixture: ReturnType<typeof montar>, rotulo: string): HTMLButtonElement {
    const todos = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    );
    const alvo = todos.find((b) => b.textContent?.trim() === rotulo);
    if (!alvo) {
      throw new Error(`Botão "${rotulo}" não encontrado`);
    }
    return alvo as HTMLButtonElement;
  }

  it('convida a criar o primeiro habito quando a lista esta vazia', () => {
    const fixture = montar([]);

    expect(texto(fixture)).toContain('Nenhum hábito ainda');
  });

  it('mostra a agenda por extenso e a sequencia', () => {
    const fixture = montar([
      habito({ tipoAgenda: 'DIAS_SEMANA', diasSemana: ['TER', 'QUI'], streak: 3 }),
    ]);

    expect(texto(fixture)).toContain('Ter, Qui');
    expect(texto(fixture)).toContain('3 dias seguidos');
  });

  it('habito diario sem sequencia diz isso', () => {
    const fixture = montar([habito()]);

    expect(texto(fixture)).toContain('Todo dia');
    expect(texto(fixture)).toContain('sem sequência');
  });

  it('marcar feito envia a data de hoje e usa a resposta do servidor', () => {
    const fixture = montar([habito()]);

    botao(fixture, 'Feito').click();

    const req = http.expectOne(`/api/v1/habitos/1/registros/${HOJE}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'FEITO' });

    // O servidor devolve o streak recalculado; a tela nao recalcula nada.
    req.flush(habito({ statusHoje: 'FEITO', streak: 1 }));
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Feito hoje');
    expect(texto(fixture)).toContain('1 dia seguido');
  });

  it('desmarcar apaga a marcacao do dia', () => {
    const fixture = montar([habito({ statusHoje: 'FEITO', streak: 1 })]);

    botao(fixture, 'Desfazer').click();

    const req = http.expectOne(`/api/v1/habitos/1/registros/${HOJE}`);
    expect(req.request.method).toBe('DELETE');

    req.flush(habito({ statusHoje: null, streak: 0 }));
    fixture.detectChanges();

    expect(texto(fixture)).toContain('sem sequência');
  });

  it('a exclusao avisa que o historico vai junto antes de apagar', () => {
    const fixture = montar([habito()]);

    botao(fixture, 'Excluir').click();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('apaga também todo o histórico');
    // Nada foi enviado ainda: a confirmacao e um passo de verdade.
    http.expectNone((r) => r.method === 'DELETE');

    botao(fixture, 'Excluir mesmo assim').click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/habitos/1').flush(null);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Nenhum hábito ainda');
  });

  it('nao envia habito de dias fixos sem nenhum dia escolhido', () => {
    const fixture = montar([]);

    botao(fixture, 'Novo hábito').click();
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    const nome = elemento.querySelector('input[formControlName="nome"]') as HTMLInputElement;
    nome.value = 'Academia';
    nome.dispatchEvent(new Event('input'));

    const agenda = elemento.querySelector('select') as HTMLSelectElement;
    agenda.value = 'DIAS_SEMANA';
    agenda.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    botao(fixture, 'Salvar').click();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Escolha ao menos um dia');
    http.expectNone((r) => r.method === 'POST');
  });

  it('cria habito de dias fixos com os dias escolhidos', () => {
    const fixture = montar([]);

    botao(fixture, 'Novo hábito').click();
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    const nome = elemento.querySelector('input[formControlName="nome"]') as HTMLInputElement;
    nome.value = 'Academia';
    nome.dispatchEvent(new Event('input'));

    const agenda = elemento.querySelector('select') as HTMLSelectElement;
    agenda.value = 'DIAS_SEMANA';
    agenda.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    botao(fixture, 'Ter').click();
    botao(fixture, 'Qui').click();
    fixture.detectChanges();

    botao(fixture, 'Salvar').click();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/habitos');
    expect(req.request.body.diasSemana).toEqual(['TER', 'QUI']);
    req.flush(habito({ id: 2, nome: 'Academia', tipoAgenda: 'DIAS_SEMANA', diasSemana: ['TER', 'QUI'] }));
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Academia');
  });

  it('o historico so e buscado quando aberto', () => {
    const fixture = montar([habito()]);

    http.expectNone((r) => r.url.includes('/historico'));

    botao(fixture, 'Histórico').click();
    http.expectOne('/api/v1/habitos/1/historico').flush([
      { data: '2026-09-06', status: 'FEITO' },
      { data: '2026-09-05', status: 'PULADO' },
    ]);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('06/09');
    expect(texto(fixture)).toContain('Pulado');
  });
});
