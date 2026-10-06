import { Injectable } from '@angular/core';

export interface MaoNaPilula {
  /** Posição em unidades de aba, quebrada enquanto o dedo arrasta a tela. */
  mover(posicao: number): void;
  /** O dedo soltou: a pílula desliza até a aba escolhida. */
  soltar(destino: number): void;
}

/**
 * Enquanto o dedo arrasta a tela, a pílula da barra acompanha. O recado vai
 * direto ao elemento, sem passar pelo Angular: a cada movimento do dedo ele
 * conferiria o app inteiro, e é isso que deixava o gesto pesado.
 */
@Injectable({ providedIn: 'root' })
export class PilulaDaBarra implements MaoNaPilula {
  private barra: MaoNaPilula | null = null;

  conectar(barra: MaoNaPilula): () => void {
    this.barra = barra;
    return () => {
      if (this.barra === barra) {
        this.barra = null;
      }
    };
  }

  mover(posicao: number): void {
    this.barra?.mover(posicao);
  }

  soltar(destino: number): void {
    this.barra?.soltar(destino);
  }
}
