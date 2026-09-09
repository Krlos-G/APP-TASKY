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
import { HabitoDoDia } from '../../core/habitos/habito.models';

function bloco(id: number, titulo: string, inicio: string, fim: string): Bloco {
  return {
    id, titulo,
    horaInicio: `${inicio}:00`,
    horaFim: `${fim}:00`,
    cor: null,
    minutosAntecedenciaLembrete: null,
  };
}

function habito(
  id: number,
  nome: string,
  status: HabitoDoDia['status'],
  streak: number,
): HabitoDoDia {
  return { id, nome, icone: null, cor: null, horaPreferida: null, status, streak };
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

  describe('habitos', () => {
    function marca(fixture: ReturnType<typeof montar>, indice = 0): HTMLButtonElement {
      const botoes = (fixture.nativeElement as HTMLElement).querySelectorAll(
        '.habito-item__marca',
      );
      return botoes[indice] as HTMLButtonElement;
    }

    it('lista os habitos do dia com o contador de feitos', () => {
      comHora(10, 0);
      const fixture = montar({
        ...DIA_COM_ROTINA,
        habitos: [habito(1, 'Ler', 'FEITO', 3), habito(2, 'Meditar', null, 0)],
      });

      const conteudo = texto(fixture);
      expect(conteudo).toContain('Hábitos de hoje');
      expect(conteudo).toContain('1/2');
      expect(conteudo).toContain('3 dias seguidos');
      expect(conteudo).toContain('começar hoje');
    });

    it('concorda o singular da sequencia', () => {
      comHora(10, 0);
      const fixture = montar({ ...DIA_COM_ROTINA, habitos: [habito(1, 'Ler', 'FEITO', 1)] });

      expect(texto(fixture)).toContain('1 dia seguido');
      expect(texto(fixture)).not.toContain('1 dias');
    });

    it('aparece mesmo sem rotina no dia', () => {
      comHora(10, 0);
      const fixture = montar({
        ...DIA_COM_ROTINA,
        temRotina: false,
        blocos: [],
        nomeDoModelo: null,
        habitos: [habito(1, 'Ler', null, 0)],
      });

      expect(texto(fixture)).toContain('Nenhuma rotina para hoje');
      expect(texto(fixture)).toContain('Hábitos de hoje');
    });

    it('marcar muda a tela antes da resposta do servidor', () => {
      comHora(10, 0);
      const fixture = montar({ ...DIA_COM_ROTINA, habitos: [habito(1, 'Ler', null, 2)] });

      marca(fixture).click();
      fixture.detectChanges();

      // Sem esperar a rede: o contador ja subiu.
      expect(texto(fixture)).toContain('1/1');

      const req = http.expectOne('/api/v1/habitos/1/registros/2026-09-02');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ status: 'FEITO' });

      req.flush({ statusHoje: 'FEITO', streak: 3 });
      fixture.detectChanges();

      // O streak so vem do servidor; o front nao tenta adivinhar.
      expect(texto(fixture)).toContain('3 dias seguidos');
    });

    it('erro do servidor desfaz a marcacao e avisa', () => {
      comHora(10, 0);
      const fixture = montar({ ...DIA_COM_ROTINA, habitos: [habito(1, 'Ler', null, 2)] });

      marca(fixture).click();
      fixture.detectChanges();
      expect(texto(fixture)).toContain('1/1');

      http.expectOne('/api/v1/habitos/1/registros/2026-09-02').flush(
        { erro: 'Nao da para marcar um dia que ainda nao chegou.' },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();

      expect(texto(fixture)).toContain('0/1');
      expect(texto(fixture)).toContain('ainda nao chegou');
    });

    it('desmarcar apaga a marcacao do dia', () => {
      comHora(10, 0);
      const fixture = montar({ ...DIA_COM_ROTINA, habitos: [habito(1, 'Ler', 'FEITO', 3)] });

      marca(fixture).click();
      fixture.detectChanges();

      const req = http.expectOne('/api/v1/habitos/1/registros/2026-09-02');
      expect(req.request.method).toBe('DELETE');

      req.flush({ statusHoje: null, streak: 2 });
      fixture.detectChanges();

      expect(texto(fixture)).toContain('0/1');
    });

    it('pular nao conta como feito', () => {
      comHora(10, 0);
      const fixture = montar({ ...DIA_COM_ROTINA, habitos: [habito(1, 'Ler', null, 2)] });

      const pular = (fixture.nativeElement as HTMLElement).querySelector(
        '.habito-item__pular',
      ) as HTMLButtonElement;
      pular.click();
      fixture.detectChanges();

      const req = http.expectOne('/api/v1/habitos/1/registros/2026-09-02');
      expect(req.request.body).toEqual({ status: 'PULADO' });

      req.flush({ statusHoje: 'PULADO', streak: 2 });
      fixture.detectChanges();

      expect(texto(fixture)).toContain('0/1');
      // Pulado nao pode ler "comecar hoje" junto: o dia ja foi resolvido.
      expect(texto(fixture)).toContain('pulado hoje');
      expect(texto(fixture)).not.toContain('começar hoje');
    });
  });
});
