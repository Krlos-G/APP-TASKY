import { Injectable, inject, signal } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';
import { filter } from 'rxjs';

/**
 * Avisa quando um deploy novo já foi baixado pelo service worker.
 *
 * Instalado no iPhone, o app fica dias suspenso sem recarregar - e o service
 * worker só procura versão nova quando a página carrega. Por isso a procura
 * acontece também toda vez que o app volta para a frente.
 */
@Injectable({ providedIn: 'root' })
export class AtualizacaoDoApp {
  private readonly sw = inject(SwUpdate);

  readonly disponivel = signal(false);

  constructor() {
    if (!this.sw.isEnabled) {
      return;
    }

    this.sw.versionUpdates
      .pipe(filter((evento) => evento.type === 'VERSION_READY'))
      .subscribe(() => this.disponivel.set(true));

    // Cache do app perdido (o iOS apaga sob pouco espaço): a versão em uso não
    // tem mais os arquivos dela, e só recarregar a recupera.
    this.sw.unrecoverable.subscribe(() => this.recarregar());

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        void this.sw.checkForUpdate();
      }
    });
  }

  recarregar(): void {
    document.location.reload();
  }
}
