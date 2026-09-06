import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Rotina } from './rotina.component';
import { ModeloDia } from '../../core/rotina/rotina.models';

function modelo(id: number, nome: string, blocos: ModeloDia['blocos'] = []): ModeloDia {
  return { id, nome, padrao: false, blocos, sobreposicoes: [] };
}

function bloco(id: number, titulo: string, inicio: string, fim: string) {
  return { id, titulo, horaInicio: inicio, horaFim: fim, cor: null, minutosAntecedenciaLembrete: null };
}

describe('Rotina', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Rotina],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Cria o componente e responde as duas chamadas iniciais. */
  function montar(modelos: ModeloDia[] = [], semana: Record<string, unknown> = {}) {
    const fixture = TestBed.createComponent(Rotina);
    fixture.detectChanges();

    http.expectOne('/api/v1/rotina/modelos').flush(modelos);
    http.expectOne('/api/v1/rotina/semana').flush({ modeloPorDia: semana });
    fixture.detectChanges();

    return fixture;
  }

  function texto(fixture: ReturnType<typeof montar>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('mostra estado vazio quando nao ha modelos', () => {
    const fixture = montar();

    expect(texto(fixture)).toContain('Nenhum modelo ainda');
  });

  it('lista os modelos com a contagem de blocos', () => {
    const fixture = montar([
      modelo(1, 'Dia útil', [bloco(10, 'Foco', '09:00', '12:00')]),
    ]);

    expect(texto(fixture)).toContain('Dia útil');
    expect(texto(fixture)).toContain('1 blocos');
  });

  it('a semana lista os sete dias', () => {
    const fixture = montar([modelo(1, 'Dia útil')]);

    const selects = (fixture.nativeElement as HTMLElement).querySelectorAll('select');
    expect(selects.length).toBe(7);
  });

  it('recusa bloco com fim antes do inicio sem ir ao servidor', () => {
    const fixture = montar([modelo(1, 'Dia útil')]);
    const componente = fixture.componentInstance as unknown as {
      alternarModelo(id: number): void;
      formBloco: { setValue(v: Record<string, string>): void };
      salvarBloco(id: number): void;
      erro(): string | null;
    };

    componente.alternarModelo(1);
    componente.formBloco.setValue({
      titulo: 'Invertido', horaInicio: '18:00', horaFim: '09:00', cor: '#000000',
    });
    componente.salvarBloco(1);

    // Validar no cliente evita uma ida ao servidor para um erro obvio; o
    // servidor valida de novo de qualquer forma.
    http.expectNone('/api/v1/rotina/modelos/1/blocos');
    expect(componente.erro()).toContain('depois da hora de início');
  });

  it('mostra a mensagem do servidor ao tentar apagar modelo em uso', () => {
    const fixture = montar([modelo(1, 'Dia útil')]);
    const componente = fixture.componentInstance as unknown as {
      apagarModelo(m: ModeloDia): void;
      erro(): string | null;
    };

    componente.apagarModelo(modelo(1, 'Dia útil'));
    http.expectOne('/api/v1/rotina/modelos/1').flush(
      { erro: 'Este modelo esta em uso em: SEG, QUA.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    // Dizer em quais dias e mais util do que "nao foi possivel apagar".
    expect(componente.erro()).toContain('SEG, QUA');
  });

  it('atribuir um dia envia os sete, porque a API substitui o conjunto', () => {
    const fixture = montar([modelo(1, 'Dia útil')], {
      SEG: null, TER: null, QUA: null, QUI: null, SEX: null, SAB: null, DOM: null,
    });
    const componente = fixture.componentInstance as unknown as {
      atribuir(dia: string, valor: string): void;
    };

    componente.atribuir('SEG', '1');

    const req = http.expectOne('/api/v1/rotina/semana');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.modeloPorDia).toEqual({
      SEG: 1, TER: null, QUA: null, QUI: null, SEX: null, SAB: null, DOM: null,
    });
    req.flush({ modeloPorDia: {} });
  });

  it('exibe o aviso de sobreposicao junto do bloco', () => {
    const fixture = montar([
      {
        ...modelo(1, 'Dia útil', [
          bloco(10, 'Expediente', '09:00', '18:00'),
          bloco(11, 'Foco', '10:00', '12:00'),
        ]),
        sobreposicoes: [
          { primeiroBlocoId: 10, segundoBlocoId: 11, descricao: '"Expediente" e "Foco" se sobrepoem' },
        ],
      },
    ]);

    (fixture.componentInstance as unknown as { alternarModelo(id: number): void })
      .alternarModelo(1);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('se sobrepoem');
  });
});
