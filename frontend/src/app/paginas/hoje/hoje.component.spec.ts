import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Hoje } from './hoje.component';
import { TimeProvider } from '../../core/tempo/time-provider.service';
import { Bloco, Dia } from '../../core/rotina/rotina.models';

function bloco(id: number, titulo: string, inicio: string, fim: string): Bloco {
  return {
    id, titulo,
    horaInicio: `${inicio}:00`,
    horaFim: `${fim}:00`,
    cor: null,
    minutosAntecedenciaLembrete: null,
  };
}

const DIA_COM_ROTINA: Dia = {
  data: '2026-09-02',
  diaSemana: 'QUA',
  temRotina: true,
  nomeDoModelo: 'Dia útil',
  blocos: [
    bloco(1, 'Treino', '07:00', '08:00'),
    bloco(2, 'Foco', '09:00', '12:00'),
    bloco(3, 'Almoço', '12:00', '13:00'),
  ],
  habitos: [],
  tarefas: [],
};

describe('Hoje', () => {
  let http: HttpTestingController;

  /**
   * Fixa a hora do dia.
   *
   * O componente pergunta ao TimeProvider que horas são; trocá-lo por uma
   * versão fixa permite testar "durante o bloco", "no intervalo" e "fim do
   * dia" sem esperar o relógio real chegar lá.
   */
  function comHora(hora: number, minuto: number) {
    const falso = {
      agora: () => new Date(2026, 8, 2, hora, minuto),
      minutosDoDia: () => hora * 60 + minuto,
      hojeIso: () => '2026-09-02',
      parar: () => undefined,
    };

    TestBed.configureTestingModule({
      imports: [Hoje],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TimeProvider, useValue: falso },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  function montar(dia: Dia) {
    const fixture = TestBed.createComponent(Hoje);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/dia').flush(dia);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof montar>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  afterEach(() => http.verify());

  it('convida a montar a rotina quando o dia nao tem nenhuma', () => {
    comHora(10, 0);
    const fixture = montar({ ...DIA_COM_ROTINA, temRotina: false, blocos: [], nomeDoModelo: null });

    expect(texto(fixture)).toContain('Nenhuma rotina para hoje');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/rotina"]'),
    ).toBeTruthy();
  });

  it('destaca o bloco em andamento e mostra quanto falta', () => {
    comHora(10, 48); // dentro de Foco (09:00–12:00)
    const fixture = montar(DIA_COM_ROTINA);

    const conteudo = texto(fixture);
    expect(conteudo).toContain('AGORA');
    expect(conteudo).toContain('Foco');
    // 72 minutos ate as 12:00 - "1h12" le melhor que "72 minutos".
    expect(conteudo).toContain('1h12');
    expect(conteudo).toContain('depois: Almoço');
  });

  it('antes do primeiro bloco, mostra o que vem a seguir', () => {
    comHora(6, 30);
    const fixture = montar(DIA_COM_ROTINA);

    const conteudo = texto(fixture);
    expect(conteudo).toContain('A SEGUIR');
    expect(conteudo).toContain('Treino');
    expect(conteudo).toContain('30 min');
  });

  it('entre dois blocos, avisa que e intervalo', () => {
    comHora(8, 30); // depois do Treino, antes do Foco
    const fixture = montar(DIA_COM_ROTINA);

    expect(texto(fixture)).toContain('INTERVALO');
    expect(texto(fixture)).toContain('Foco');
  });

  it('depois do ultimo bloco, encerra o dia', () => {
    comHora(20, 0);
    const fixture = montar(DIA_COM_ROTINA);

    const conteudo = texto(fixture);
    expect(conteudo).toContain('FIM DO DIA');
    expect(conteudo).toContain('Nada mais programado');
  });

  it('esmaece os blocos que ja terminaram', () => {
    comHora(10, 0); // Treino acabou; Foco em andamento
    const fixture = montar(DIA_COM_ROTINA);

    const itens = (fixture.nativeElement as HTMLElement).querySelectorAll('.linha__item');
    expect(itens[0].classList).toContain('linha__item--passado');
    expect(itens[1].classList).toContain('linha__item--atual');
    expect(itens[2].classList).not.toContain('linha__item--passado');
  });

  it('mostra a data por extenso sem cair no dia anterior', () => {
    comHora(10, 0);
    const fixture = montar(DIA_COM_ROTINA);

    // Montar a data com numeros, e nao com new Date('2026-09-02'), evita o
    // parse em UTC que jogaria o dia para tras em fuso negativo.
    expect(texto(fixture)).toContain('2 de setembro');
  });
});
