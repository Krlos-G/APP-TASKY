import { DestroyRef, Directive, ElementRef, computed, inject, linkedSignal, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
} from '@angular/router';
import { ABAS, indiceDaAba, indiceDaSecao } from './abas';
import { indicePelaPosicao } from './gestos';
import { PilulaDaBarra } from './pilula-da-barra';

/** O quanto o dedo anda antes de um toque virar arrasto. */
const LIMIAR_ARRASTO = 8;

/**
 * Arrastar o dedo pela barra leva a pílula junto; a aba troca ao soltar.
 *
 * Trocar no caminho montaria cada tela por onde o dedo passasse, com as
 * buscas dela no servidor - e a pílula sob o dedo já dá a sensação toda.
 */
@Directive({
  selector: '[appBarraDeslizavel]',
  host: {
    '[style.--pilula]': 'lugarDaPilula()',
    '[style.--abas]': 'total',
    '[class.nav__trilho--arrastando]': 'arrastando()',
    '[class.nav__trilho--sem-aba]': 'posicao() < 0',
    '(pointerdown)': 'comecar($event)',
    '(pointermove)': 'mover($event)',
    '(pointerup)': 'soltar()',
    '(pointercancel)': 'cancelar()',
  },
})
export class BarraDeslizavel {
  private readonly router = inject(Router);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly total = ABAS.length;
  private readonly ativa = signal(indiceDaSecao(this.router.url));
  /** Vale enquanto o dedo arrasta, e até a navegação escolhida terminar. */
  private readonly sobrescrita = signal<number | null>(null);
  protected readonly arrastando = signal(false);
  protected readonly posicao = computed(() => this.sobrescrita() ?? this.ativa());
  /** Fora das abas a pílula some onde estava, em vez de escorregar para fora da barra. */
  protected readonly lugarDaPilula = linkedSignal<number, number>({
    source: this.posicao,
    computation: (posicao, anterior) => (posicao >= 0 ? posicao : (anterior?.value ?? 0)),
  });

  private inicioX: number | null = null;
  private engolirClique = false;

  constructor() {
    this.router.events.pipe(takeUntilDestroyed()).subscribe((evento) => {
      if (evento instanceof NavigationStart && !this.arrastando()) {
        // A pílula anda já no toque, sem esperar a tela nova carregar.
        const destino = indiceDaSecao(evento.url);
        this.sobrescrita.set(destino >= 0 ? destino : null);
      } else if (
        evento instanceof NavigationEnd ||
        evento instanceof NavigationCancel ||
        evento instanceof NavigationError
      ) {
        this.ativa.set(indiceDaSecao(this.router.url));
        this.sobrescrita.set(null);
      }
    });

    // O dedo arrastando a tela leva a pilula junto, escrevendo direto no
    // elemento: o Angular so volta a mandar nela quando a navegacao comecar.
    const desconectar = inject(PilulaDaBarra).conectar({
      mover: (posicao) => {
        this.elemento.classList.add('nav__trilho--arrastando');
        this.elemento.style.setProperty('--pilula', String(posicao));
      },
      soltar: (destino) => {
        // Sem a classe, a transicao volta: a pilula desliza de onde o dedo
        // largou ate a aba escolhida.
        this.elemento.classList.remove('nav__trilho--arrastando');
        this.elemento.style.setProperty('--pilula', String(destino));
      },
    });
    inject(DestroyRef).onDestroy(desconectar);

    // O arrasto termina com o dedo sobre um link; sem isto o clique dele
    // navegaria de novo. Captura, porque o link recebe o clique antes da barra.
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
    // No desktop a barra é uma coluna lateral: lá não existe arrastar de lado.
    if (!evento.isPrimary || matchMedia('(min-width: 768px)').matches) {
      return;
    }
    this.inicioX = evento.clientX;
    this.engolirClique = false;
  }

  protected mover(evento: PointerEvent): void {
    if (this.inicioX === null) {
      return;
    }
    if (!this.arrastando()) {
      if (Math.abs(evento.clientX - this.inicioX) < LIMIAR_ARRASTO) {
        return;
      }
      this.arrastando.set(true);
      this.elemento.setPointerCapture(evento.pointerId);
    }
    const caixa = this.elemento.getBoundingClientRect();
    this.sobrescrita.set(indicePelaPosicao(evento.clientX - caixa.left, caixa.width, this.total));
  }

  protected soltar(): void {
    this.inicioX = null;
    if (!this.arrastando()) {
      return;
    }
    this.arrastando.set(false);
    this.engolirClique = true;
    setTimeout(() => (this.engolirClique = false));

    const destino = Math.round(this.sobrescrita() ?? this.ativa());
    if (destino === indiceDaAba(this.router.url)) {
      this.sobrescrita.set(null);
      return;
    }
    // Encaixa na aba de destino já, com a transição da pílula de volta ligada.
    this.sobrescrita.set(destino);
    void this.router.navigateByUrl(ABAS[destino].rota);
  }

  protected cancelar(): void {
    this.inicioX = null;
    this.arrastando.set(false);
    this.sobrescrita.set(null);
  }
}
