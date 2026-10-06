import { Injectable } from '@angular/core';
import { Observable, Subject, filter, map } from 'rxjs';

/**
 * As abas ficam vivas na trilha, então não recarregam sozinhas ao serem
 * reabertas. Este aviso diz quando uma aba voltou a ficar visível, para ela
 * atualizar os dados — marcar um hábito no Hoje e ir para Hábitos tem de
 * mostrar o hábito marcado.
 */
@Injectable({ providedIn: 'root' })
export class AbaVisivel {
  private readonly mudou = new Subject<string>();

  /** Não avisa na primeira vez: aí a tela acabou de carregar. */
  voltou(rota: string): Observable<void> {
    return this.mudou.pipe(
      filter((visivel) => visivel === rota),
      map(() => undefined),
    );
  }

  avisar(rota: string): void {
    this.mudou.next(rota);
  }
}
