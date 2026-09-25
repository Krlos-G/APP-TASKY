import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Resumo } from './resumo.component';
import { Resumo as ResumoDoDia } from '../../core/resumo/resumo.models';

function resumo(parcial: Partial<ResumoDoDia> = {}): ResumoDoDia {
  return {
    data: '2026-09-23',
    dia: {
      habitosFeitos: 1,
      habitosDevidos: 3,
      tarefasFeitas: 1,
      tarefasDoDia: 1,
      atrasadas: 0,
    },
    semana: {
      inicio: '2026-09-21',
      fim: '2026-09-23',
      habitosFeitos: 5,
      habitosCobrados: 9,
      tarefasConcluidas: 4,
    },
    streaks: [],
    ...parcial,
  };
}

describe('Resumo', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Resumo],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function montar(dados: ResumoDoDia) {
    const fixture = TestBed.createComponent(Resumo);
    fixture.detectChanges();
    http.expectOne('/api/v1/resumo').flush(dados);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof montar>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function barras(fixture: ReturnType<typeof montar>): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('[role="progressbar"]'),
    );
  }

  it('mostra os numeros do dia e da semana', () => {
    const fixture = montar(resumo());

    const conteudo = texto(fixture);
    expect(conteudo).toContain('1/3');
    expect(conteudo).toContain('5/9');
    expect(conteudo).toContain('4');
    expect(conteudo).toContain('tarefas concluídas');
    // Sem atrasadas, o número nem aparece.
    expect(conteudo).not.toContain('atrasada');
  });

  it('a barra do dia soma habitos e tarefas', () => {
    const fixture = montar(resumo());

    // 2 feitos de 4 itens: 1 habito + 1 tarefa.
    expect(barras(fixture)[0].getAttribute('aria-valuenow')).toBe('2');
    expect(barras(fixture)[0].getAttribute('aria-valuemax')).toBe('4');
    expect(texto(fixture)).toContain('50%');
  });

  it('dia sem nada planejado nao mostra barra, e sim um aviso', () => {
    const fixture = montar(
      resumo({
        dia: {
          habitosFeitos: 0,
          habitosDevidos: 0,
          tarefasFeitas: 0,
          tarefasDoDia: 0,
          atrasadas: 0,
        },
      }),
    );

    expect(texto(fixture)).toContain('Nada planejado para hoje.');
    // So sobra a barra da semana.
    expect(barras(fixture).length).toBe(1);
  });

  it('o periodo da semana sai por extenso, sem repetir o mes', () => {
    const fixture = montar(resumo());

    expect(texto(fixture)).toContain('21 a 23 de setembro');
  });

  it('semana que atravessa o mes nomeia os dois', () => {
    const fixture = montar(
      resumo({
        semana: {
          inicio: '2026-09-28',
          fim: '2026-10-01',
          habitosFeitos: 2,
          habitosCobrados: 4,
          tarefasConcluidas: 0,
        },
      }),
    );

    expect(texto(fixture)).toContain('28 de setembro a 1 de outubro');
  });

  it('a contagem de atrasadas aparece quando ha alguma', () => {
    const comAtraso = montar(
      resumo({
        dia: {
          habitosFeitos: 0,
          habitosDevidos: 1,
          tarefasFeitas: 0,
          tarefasDoDia: 0,
          atrasadas: 1,
        },
      }),
    );

    expect(texto(comAtraso)).toContain('atrasada');
  });

  it('lista as sequencias na ordem que o servidor mandou', () => {
    const fixture = montar(
      resumo({
        streaks: [
          { id: 1, nome: 'Ler', cor: null, streak: 7 },
          { id: 2, nome: 'Meditar', cor: null, streak: 1 },
          { id: 3, nome: 'Correr', cor: null, streak: 0 },
        ],
      }),
    );

    const nomes = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.sequencia__nome'),
    ).map((n) => n.textContent?.trim());

    expect(nomes).toEqual(['Ler', 'Meditar', 'Correr']);
    expect(texto(fixture)).toContain('7 dias seguidos');
    expect(texto(fixture)).toContain('1 dia seguido');
    expect(texto(fixture)).toContain('sem sequência');
  });

  it('sem habito nenhum, convida a criar o primeiro', () => {
    const fixture = montar(resumo());

    expect(texto(fixture)).toContain('Nenhum hábito ainda');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/habitos"]'),
    ).toBeTruthy();
  });

  it('erro do servidor aparece na tela', () => {
    const fixture = TestBed.createComponent(Resumo);
    fixture.detectChanges();
    http.expectOne('/api/v1/resumo').flush(
      { erro: 'Sessao expirada. Entre novamente.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sessao expirada');
  });
});
