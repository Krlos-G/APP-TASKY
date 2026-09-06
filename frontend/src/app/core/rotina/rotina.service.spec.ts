import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { RotinaService } from './rotina.service';
import { DiaService } from './dia.service';
import { DiaSemana, ModeloDia } from './rotina.models';

function modeloVazio(id: number, nome: string): ModeloDia {
  return { id, nome, padrao: false, blocos: [], sobreposicoes: [] };
}

describe('RotinaService', () => {
  let service: RotinaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RotinaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista os modelos', () => {
    let recebidos: ModeloDia[] = [];
    service.listarModelos().subscribe((m) => (recebidos = m));

    const req = http.expectOne('/api/v1/rotina/modelos');
    expect(req.request.method).toBe('GET');
    req.flush([modeloVazio(1, 'Dia útil')]);

    expect(recebidos.length).toBe(1);
    expect(recebidos[0].nome).toBe('Dia útil');
  });

  it('cria modelo', () => {
    service.criarModelo({ nome: 'Fim de semana', padrao: false }).subscribe();

    const req = http.expectOne('/api/v1/rotina/modelos');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nome: 'Fim de semana', padrao: false });
    req.flush(modeloVazio(2, 'Fim de semana'));
  });

  it('adicionar bloco devolve o modelo inteiro, com os avisos recalculados', () => {
    let recebido: ModeloDia | null = null;
    service
      .adicionarBloco(1, { titulo: 'Foco', horaInicio: '09:00', horaFim: '12:00' })
      .subscribe((m) => (recebido = m));

    const req = http.expectOne('/api/v1/rotina/modelos/1/blocos');
    req.flush({
      ...modeloVazio(1, 'Dia útil'),
      blocos: [
        { id: 10, titulo: 'Expediente', horaInicio: '09:00', horaFim: '18:00', cor: null, minutosAntecedenciaLembrete: null },
        { id: 11, titulo: 'Foco', horaInicio: '10:00', horaFim: '12:00', cor: null, minutosAntecedenciaLembrete: null },
      ],
      sobreposicoes: [
        { primeiroBlocoId: 10, segundoBlocoId: 11, descricao: '"Expediente" e "Foco" se sobrepoem' },
      ],
    });

    // O servico nao filtra nem interpreta: quem decide o que fazer com o aviso
    // e a tela.
    expect(recebido!.sobreposicoes.length).toBe(1);
  });

  it('define a semana mandando os sete dias', () => {
    const semana: Record<DiaSemana, number | null> = {
      SEG: 1, TER: 1, QUA: 1, QUI: 1, SEX: 1, SAB: null, DOM: null,
    };
    service.definirSemana(semana).subscribe();

    const req = http.expectOne('/api/v1/rotina/semana');
    expect(req.request.method).toBe('PUT');
    // Substituicao do conjunto: os dias sem rotina vao explicitamente nulos.
    expect(req.request.body).toEqual({ modeloPorDia: semana });
    req.flush({ modeloPorDia: {} });
  });

  it('apaga bloco e modelo', () => {
    service.apagarBloco(7).subscribe();
    expect(http.expectOne('/api/v1/rotina/blocos/7').request.method).toBe('DELETE');
    http.expectNone('/api/v1/rotina/blocos/7');

    service.apagarModelo(3).subscribe();
    expect(http.expectOne('/api/v1/rotina/modelos/3').request.method).toBe('DELETE');
  });
});

describe('DiaService', () => {
  let service: DiaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DiaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sem data, nao envia parametro: o servidor decide que dia e hoje', () => {
    service.buscar().subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/dia');
    expect(req.request.params.has('data')).toBe(false);
    req.flush({});
  });

  it('com data, envia o parametro', () => {
    service.buscar('2026-09-05').subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/dia');
    expect(req.request.params.get('data')).toBe('2026-09-05');
    req.flush({});
  });
});
