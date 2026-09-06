import { TestBed } from '@angular/core/testing';
import { TimeProvider } from './time-provider.service';

describe('TimeProvider', () => {
  let service: TimeProvider;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.inject(TimeProvider);
  });

  afterEach(() => {
    service.parar();
    vi.useRealTimers();
  });

  it('formata hoje no padrao ISO que a API espera', () => {
    vi.setSystemTime(new Date(2026, 8, 5, 14, 30)); // 5 de setembro de 2026
    service['agoraAtual'].set(new Date());

    // Mes com zero a esquerda: '2026-9-5' seria recusado pelo backend.
    expect(service.hojeIso()).toBe('2026-09-05');
  });

  it('converte a hora atual em minutos desde a meia-noite', () => {
    vi.setSystemTime(new Date(2026, 8, 5, 9, 45));
    service['agoraAtual'].set(new Date());

    expect(service.minutosDoDia()).toBe(9 * 60 + 45);
  });

  it('atualiza sozinho a cada minuto', () => {
    const inicial = service.agora();

    vi.advanceTimersByTime(60_000);

    expect(service.agora()).not.toBe(inicial);
  });

  it('nao atualiza antes do minuto fechar', () => {
    const inicial = service.agora();

    vi.advanceTimersByTime(59_000);

    // Atualizar a cada segundo so gastaria renderizacao: a tela mostra
    // "faltam 1h12", que nao muda mais rapido que isso.
    expect(service.agora()).toBe(inicial);
  });
});
