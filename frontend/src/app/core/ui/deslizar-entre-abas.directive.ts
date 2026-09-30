import { Directive, ElementRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { abaVizinha, indiceDaAba } from './abas';
import { Eixo, decidirEixo, deveTrocar } from './gestos';

/**
 * Deslizar a tela para o lado troca para a aba vizinha, ao soltar.
 *
 * A tela acompanha o dedo com `left`, e não com `transform`: um transform aqui
 * faria o botão flutuante, que é `position: fixed` e mora dentro da página,
 * passar a se posicionar a partir dela - e ele pularia para o fim da lista.
 */
@Directive({
  selector: '[appDeslizarEntreAbas]',
  host: {
    '[style.left.px]': 'deslocamento()',
    '[class.conteudo--deslizando]': 'deslizando()',
    '[class.conteudo--trocando]': 'trocando()',
    '(pointerdown)': 'comecar($event)',
    '(pointermove)': 'mover($event)',
    '(pointerup)': 'soltar($event)',
    '(pointercancel)': 'desistir()',
  },
})
export class DeslizarEntreAbas {
  private readonly router = inject(Router);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly deslocamento = signal(0);
  protected readonly deslizando = signal(false);
  /** Ao trocar, a tela volta ao lugar sem animar: quem anima é a que chega. */
  protected readonly trocando = signal(false);

  private inicio: { x: number; y: number } | null = null;
  private eixo: Eixo | null = null;
  private ultimo = { x: 0, t: 0 };
  private velocidade = 0;
  private engolirClique = false;

  constructor() {
    this.elemento.addEventListener(
      'click',
      (evento) => {
        if (this.engolirClique) {
          evento.preventDefault();
          evento.stopPropagation();
        }
      },
      true,
    );
  }

  protected comecar(evento: PointerEvent): void {
    this.trocando.set(false);
    // Só o dedo desliza (o mouse seleciona texto), só nas abas, e nunca a
    // partir de um formulário: lá o gesto é editar, não trocar de tela.
    const alvo = evento.target as Element;
    if (
      evento.pointerType === 'mouse' ||
      !evento.isPrimary ||
      indiceDaAba(this.router.url) < 0 ||
      alvo.closest('form, input, textarea, select')
    ) {
      return;
    }
    this.inicio = { x: evento.clientX, y: evento.clientY };
    this.eixo = null;
    this.ultimo = { x: evento.clientX, t: evento.timeStamp };
    this.velocidade = 0;
  }

  protected mover(evento: PointerEvent): void {
    if (!this.inicio) {
      return;
    }
    const dx = evento.clientX - this.inicio.x;
    const dy = evento.clientY - this.inicio.y;

    if (this.eixo === null) {
      this.eixo = decidirEixo(dx, dy);
      if (this.eixo === 'vertical') {
        this.inicio = null;
        return;
      }
      if (this.eixo === null) {
        return;
      }
      this.deslizando.set(true);
      this.elemento.setPointerCapture(evento.pointerId);
    }

    const intervalo = Math.max(1, evento.timeStamp - this.ultimo.t);
    this.velocidade = (evento.clientX - this.ultimo.x) / intervalo;
    this.ultimo = { x: evento.clientX, t: evento.timeStamp };

    // Sem aba daquele lado, a tela resiste: dá para sentir que ali acabou.
    const temVizinha = abaVizinha(this.router.url, dx < 0 ? 1 : -1) !== null;
    this.deslocamento.set(dx * (temVizinha ? 0.4 : 0.12));
  }

  protected soltar(evento: PointerEvent): void {
    if (!this.inicio || this.eixo !== 'horizontal') {
      this.inicio = null;
      return;
    }
    const dx = evento.clientX - this.inicio.x;
    const vizinha = abaVizinha(this.router.url, dx < 0 ? 1 : -1);
    this.inicio = null;
    this.deslizando.set(false);
    this.engolirClique = true;
    setTimeout(() => (this.engolirClique = false));

    if (vizinha && deveTrocar(dx, this.elemento.clientWidth, this.velocidade)) {
      this.trocando.set(true);
      this.deslocamento.set(0);
      void this.router.navigateByUrl(vizinha.rota);
    } else {
      this.deslocamento.set(0);
    }
  }

  protected desistir(): void {
    this.inicio = null;
    this.deslizando.set(false);
    this.deslocamento.set(0);
  }
}
