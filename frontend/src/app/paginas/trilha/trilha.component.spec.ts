import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AbaVisivel } from '../../core/ui/aba-visivel';
import { casarAba } from '../../core/ui/abas';
import { Trilha } from './trilha.component';

let criadas: Record<string, number>;

@Component({ selector: 'app-hoje', template: 'tela hoje' })
class HojeFalsa {
  constructor() {
    criadas['hoje']++;
  }
}

@Component({ selector: 'app-resumo', template: 'tela resumo' })
class ResumoFalso {
  constructor() {
    criadas['resumo']++;
  }
}

@Component({ selector: 'app-habitos', template: 'tela habitos' })
class HabitosFalsa {
  constructor() {
    criadas['habitos']++;
  }
}

@Component({ selector: 'app-tarefas', template: 'tela tarefas' })
class TarefasFalsa {
  constructor() {
    criadas['tarefas']++;
  }
}

describe('Trilha', () => {
  let harness: RouterTestingHarness;
  let rolarPara: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    criadas = { hoje: 0, resumo: 0, habitos: 0, tarefas: 0 };
    TestBed.configureTestingModule({
      providers: [provideRouter([{ matcher: casarAba, component: Trilha }])],
    });
    TestBed.overrideComponent(Trilha, {
      set: { imports: [HojeFalsa, ResumoFalso, HabitosFalsa, TarefasFalsa] },
    });
    rolarPara = vi.fn();
    vi.spyOn(window, 'scrollTo').mockImplementation(rolarPara as never);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    rolar(0);
  });

  function rolar(y: number): void {
    Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
  }

  function visiveis(): string[] {
    return Array.from(
      (harness.routeNativeElement as HTMLElement).querySelectorAll('.trilha__aba--visivel'),
    ).map((aba) => aba.textContent?.trim() ?? '');
  }

  async function ir(url: string): Promise<void> {
    await harness.navigateByUrl(url);
    // A troca espera a anterior terminar numa fila de promessas.
    await new Promise((fim) => setTimeout(fim));
    harness.detectChanges();
  }

  it('abre so a aba da URL, com as quatro montadas', async () => {
    await ir('/resumo');

    expect(visiveis()).toEqual(['tela resumo']);
    expect(criadas).toEqual({ hoje: 1, resumo: 1, habitos: 1, tarefas: 1 });
  });

  it('trocar de aba nao recria nenhuma tela', async () => {
    await ir('/hoje');
    const trilha = harness.routeDebugElement?.componentInstance;

    await ir('/habitos');

    expect(visiveis()).toEqual(['tela habitos']);
    expect(harness.routeDebugElement?.componentInstance).toBe(trilha);
    expect(criadas).toEqual({ hoje: 1, resumo: 1, habitos: 1, tarefas: 1 });
  });

  it('avisa a aba que voltou a ficar visivel, e nao a que acabou de abrir', async () => {
    const avisos: string[] = [];
    vi.spyOn(TestBed.inject(AbaVisivel), 'avisar').mockImplementation((rota) => avisos.push(rota));

    await ir('/hoje');
    await ir('/tarefas');

    expect(avisos).toEqual(['/tarefas']);
  });

  it('o titulo acompanha a aba', async () => {
    await ir('/hoje');
    await ir('/habitos');

    expect(TestBed.inject(Title).getTitle()).toBe('Hábitos · Tasky');
  });

  it('cada aba guarda a propria rolagem', async () => {
    await ir('/hoje');
    rolar(300);

    await ir('/resumo');
    expect(rolarPara).toHaveBeenLastCalledWith(0, 0);

    rolar(40);
    await ir('/hoje');
    expect(rolarPara).toHaveBeenLastCalledWith(0, 300);
  });

  describe('o gesto', () => {
    // A tela de teste tem 1024 px de largura: um quarto dela são 256 px.
    let agora: number;

    beforeEach(() => {
      agora = 0;
      vi.spyOn(performance, 'now').mockImplementation(() => agora);
    });

    function dedo(tipo: string, x: number, y: number, extra: Record<string, unknown> = {}): Event {
      const evento = new Event(tipo, { bubbles: true });
      return Object.assign(evento, {
        clientX: x,
        clientY: y,
        pointerId: 1,
        pointerType: 'touch',
        isPrimary: true,
        ...extra,
      });
    }

    /** O dedo encosta, anda de x em x ao longo do tempo e solta. */
    async function deslizar(
      caminho: { x: number; y?: number; ms: number }[],
      alvo?: Element,
    ): Promise<void> {
      const onde = alvo ?? (harness.routeNativeElement as HTMLElement).querySelector('.trilha__aba--visivel')!;
      const [inicio, ...resto] = caminho;
      agora = inicio.ms;
      onde.dispatchEvent(dedo('pointerdown', inicio.x, inicio.y ?? 400));
      for (const ponto of resto) {
        agora = ponto.ms;
        onde.dispatchEvent(dedo('pointermove', ponto.x, ponto.y ?? 400));
      }
      const fim = caminho[caminho.length - 1];
      onde.dispatchEvent(dedo('pointerup', fim.x, fim.y ?? 400));
      await new Promise((pronto) => setTimeout(pronto));
      harness.detectChanges();
    }

    function rota(): string {
      return TestBed.inject(Router).url;
    }

    it('deslizar para a esquerda passa para a aba seguinte', async () => {
      await ir('/hoje');

      await deslizar([
        { x: 600, ms: 0 },
        { x: 580, ms: 100 },
        { x: 300, ms: 800 },
      ]);

      expect(rota()).toBe('/resumo');
      expect(visiveis()).toEqual(['tela resumo']);
    });

    it('deslizar para a direita volta para a aba anterior', async () => {
      await ir('/habitos');

      await deslizar([
        { x: 300, ms: 0 },
        { x: 320, ms: 100 },
        { x: 650, ms: 800 },
      ]);

      expect(rota()).toBe('/resumo');
    });

    it('deslize curto e lento desiste e fica na mesma aba', async () => {
      await ir('/hoje');

      await deslizar([
        { x: 600, ms: 0 },
        { x: 580, ms: 400 },
        { x: 540, ms: 1200 },
      ]);

      expect(rota()).toBe('/hoje');
      expect(visiveis()).toEqual(['tela hoje']);
      expect(document.querySelector('.trilha__aba--quadro')).toBeNull();
    });

    it('um peteleco curto e rapido troca', async () => {
      await ir('/hoje');

      await deslizar([
        { x: 600, ms: 0 },
        { x: 580, ms: 10 },
        { x: 540, ms: 50 },
      ]);

      expect(rota()).toBe('/resumo');
    });

    it('arrastar na vertical e rolagem, nao troca de aba', async () => {
      await ir('/hoje');

      await deslizar([
        { x: 600, y: 400, ms: 0 },
        { x: 590, y: 300, ms: 100 },
        { x: 500, y: 100, ms: 800 },
      ]);

      expect(rota()).toBe('/hoje');
    });

    it('na primeira aba, puxar para a direita nao leva a lugar nenhum', async () => {
      await ir('/hoje');

      await deslizar([
        { x: 300, ms: 0 },
        { x: 320, ms: 100 },
        { x: 800, ms: 800 },
      ]);

      expect(rota()).toBe('/hoje');
    });

    it('o mouse nao arrasta a tela, para nao brigar com a selecao de texto', async () => {
      await ir('/hoje');
      const aba = (harness.routeNativeElement as HTMLElement).querySelector('.trilha__aba--visivel')!;

      aba.dispatchEvent(dedo('pointerdown', 600, 400, { pointerType: 'mouse' }));
      aba.dispatchEvent(dedo('pointermove', 580, 400, { pointerType: 'mouse' }));
      aba.dispatchEvent(dedo('pointermove', 200, 400, { pointerType: 'mouse' }));
      aba.dispatchEvent(dedo('pointerup', 200, 400, { pointerType: 'mouse' }));
      await new Promise((pronto) => setTimeout(pronto));

      expect(rota()).toBe('/hoje');
    });

    it('gesto que comeca num campo de formulario e do campo', async () => {
      await ir('/hoje');
      const campo = document.createElement('input');
      (harness.routeNativeElement as HTMLElement).querySelector('.trilha__aba--visivel')!.append(campo);

      await deslizar(
        [
          { x: 600, ms: 0 },
          { x: 580, ms: 100 },
          { x: 200, ms: 800 },
        ],
        campo,
      );

      expect(rota()).toBe('/hoje');
    });
  });
});
