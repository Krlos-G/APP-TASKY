import { TestBed } from '@angular/core/testing';
import { SwUpdate, VersionEvent } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { AtualizacaoDoApp } from './atualizacao';

describe('AtualizacaoDoApp', () => {
  let versoes: Subject<VersionEvent>;
  let irrecuperavel: Subject<{ type: 'UNRECOVERABLE_STATE'; reason: string }>;
  let checkForUpdate: ReturnType<typeof vi.fn>;

  function criar(ligado = true): AtualizacaoDoApp {
    versoes = new Subject();
    irrecuperavel = new Subject();
    checkForUpdate = vi.fn().mockResolvedValue(false);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SwUpdate,
          useValue: {
            isEnabled: ligado,
            versionUpdates: versoes,
            unrecoverable: irrecuperavel,
            checkForUpdate,
          },
        },
      ],
    });
    return TestBed.inject(AtualizacaoDoApp);
  }

  function voltarParaAFrente(): void {
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
  }

  it('avisa quando a versao nova ja foi baixada', () => {
    const atualizacao = criar();

    versoes.next({
      type: 'VERSION_READY',
      currentVersion: { hash: 'a' },
      latestVersion: { hash: 'b' },
    });

    expect(atualizacao.disponivel()).toBe(true);
  });

  it('nao avisa enquanto a versao nova ainda esta baixando', () => {
    const atualizacao = criar();

    versoes.next({ type: 'VERSION_DETECTED', version: { hash: 'b' } });

    expect(atualizacao.disponivel()).toBe(false);
  });

  it('procura versao nova sempre que o app volta para a frente', () => {
    criar();

    voltarParaAFrente();

    expect(checkForUpdate).toHaveBeenCalledTimes(1);
  });

  it('recarrega sozinho se o cache do app se perdeu', () => {
    const atualizacao = criar();
    const recarregar = vi.spyOn(atualizacao, 'recarregar').mockImplementation(() => undefined);

    irrecuperavel.next({ type: 'UNRECOVERABLE_STATE', reason: 'cache apagado' });

    expect(recarregar).toHaveBeenCalled();
  });

  it('sem service worker (desenvolvimento) nao faz nada', () => {
    const atualizacao = criar(false);

    voltarParaAFrente();

    expect(checkForUpdate).not.toHaveBeenCalled();
    expect(atualizacao.disponivel()).toBe(false);
  });
});
