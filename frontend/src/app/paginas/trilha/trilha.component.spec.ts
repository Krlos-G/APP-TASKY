import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
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
});
