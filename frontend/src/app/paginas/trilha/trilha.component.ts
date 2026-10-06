import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  NgZone,
  inject,
  signal,
  viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AbaVisivel } from '../../core/ui/aba-visivel';
import { ABAS, indiceDaAba } from '../../core/ui/abas';
import { Eixo, decidirEixo, deveTrocar } from '../../core/ui/gestos';
import { PilulaDaBarra } from '../../core/ui/pilula-da-barra';
import { Habitos } from '../habitos/habitos.component';
import { Hoje } from '../hoje/hoje.component';
import { Resumo } from '../resumo/resumo.component';
import { Tarefas } from '../tarefas/tarefas.component';

/** A curva do iOS, a mesma de `--curva`. */
const CURVA = 'cubic-bezier(0.32, 0.72, 0, 1)';
const DURACAO = 320;
/** Nas pontas não há aba para onde ir: a tela resiste ao dedo. */
const RESISTENCIA = 0.25;

interface Movimento {
  indice: number;
  de: string;
  para: string;
}

interface Gesto {
  pointerId: number;
  inicioX: number;
  inicioY: number;
  eixo: Eixo | null;
  dx: number;
  /** O quanto as abas andaram de fato: igual ao dx, menos nas pontas. */
  deslocamento: number;
  ultimoX: number;
  ultimoT: number;
  velocidade: number;
  largura: number;
  quadros: number[];
  pedido: number | null;
}

/**
 * As quatro abas montadas lado a lado, como num app nativo: trocar de aba não
 * destrói uma tela para criar outra, e por isso não busca tudo de novo.
 *
 * Em repouso, só a aba visível está na página, e a rolagem é a da página
 * inteira — é o que mantém o "toque no relógio sobe ao topo" do iPhone. Só
 * durante o movimento as abas viram quadros fixos lado a lado.
 */
@Component({
  selector: 'app-trilha',
  imports: [Hoje, Resumo, Habitos, Tarefas],
  templateUrl: './trilha.component.html',
  styleUrl: './trilha.component.scss',
})
export class Trilha {
  private readonly titulo = inject(Title);
  private readonly abaVisivel = inject(AbaVisivel);
  private readonly pilula = inject(PilulaDaBarra);
  private readonly router = inject(Router);
  private readonly zona = inject(NgZone);
  private readonly detector = inject(ChangeDetectorRef);
  private readonly palco = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly abas = viewChildren<ElementRef<HTMLElement>>('aba');

  protected readonly visivel = signal(-1);

  private readonly rolagens = ABAS.map(() => 0);
  /** Para onde a URL já mandou, mesmo com uma troca ainda na fila. */
  private alvo = -1;
  private animacoes: Animation[] = [];
  private fila = Promise.resolve();
  private gesto: Gesto | null = null;
  private engolirClique = false;

  constructor() {
    inject(ActivatedRoute)
      .url.pipe(takeUntilDestroyed())
      .subscribe((segmentos) => this.abrir(indiceDaAba('/' + (segmentos[0]?.path ?? ''))));

    // O dedo fica fora do Angular: a cada movimento ele conferiria o app
    // inteiro, e era isso que deixava o gesto pesado no iPhone.
    this.zona.runOutsideAngular(() => {
      this.palco.addEventListener('pointerdown', (evento) => this.comecar(evento));
      this.palco.addEventListener('pointermove', (evento) => this.mover(evento));
      this.palco.addEventListener('pointerup', (evento) => this.soltar(evento));
      this.palco.addEventListener('pointercancel', (evento) => this.soltar(evento, true));
      // O dedo solta sobre um link depois de arrastar; o clique dele não vale.
      this.palco.addEventListener(
        'click',
        (evento) => {
          if (this.engolirClique) {
            evento.preventDefault();
            evento.stopPropagation();
          }
        },
        true,
      );
    });
  }

  // --------------------------------------------------------- troca pela URL

  private abrir(destino: number): void {
    if (destino < 0 || destino === this.alvo) {
      return;
    }
    this.titulo.setTitle(`${ABAS[destino].rotulo} · Tasky`);

    if (this.alvo < 0) {
      this.alvo = destino;
      this.visivel.set(destino);
      return;
    }

    this.alvo = destino;
    this.abaVisivel.avisar(ABAS[destino].rota);
    // Uma troca no meio da outra: a anterior termina na hora e esta vem atrás.
    this.animacoes.forEach((animacao) => animacao.finish());
    this.enfileirar(() => this.trocar(destino));
  }

  private async trocar(para: number): Promise<void> {
    const de = this.visivel();
    if (de === para) {
      return;
    }
    if (!podeAnimar(this.palco)) {
      this.rolagens[de] = window.scrollY;
      this.concluir(para);
      return;
    }

    const sentido = para > de ? 1 : -1;
    this.montarPalco(de, [de, para]);
    try {
      await this.animar([
        { indice: de, de: 'translateX(0)', para: `translateX(${-sentido * 100}%)` },
        { indice: para, de: `translateX(${sentido * 100}%)`, para: 'translateX(0)' },
      ]);
    } finally {
      this.desmontarPalco([de, para]);
    }
    this.concluir(para);
  }

  // ---------------------------------------------------------------- o dedo

  private comecar(evento: PointerEvent): void {
    // Só o dedo desliza (o mouse seleciona texto), e nunca a partir de um
    // campo: lá o gesto é editar, não trocar de aba.
    if (
      evento.pointerType === 'mouse' ||
      !evento.isPrimary ||
      this.animacoes.length > 0 ||
      this.visivel() < 0 ||
      (evento.target as Element).closest('form, input, textarea, select')
    ) {
      return;
    }
    this.gesto = {
      pointerId: evento.pointerId,
      inicioX: evento.clientX,
      inicioY: evento.clientY,
      eixo: null,
      dx: 0,
      deslocamento: 0,
      ultimoX: evento.clientX,
      ultimoT: performance.now(),
      velocidade: 0,
      largura: 0,
      quadros: [],
      pedido: null,
    };
  }

  private mover(evento: PointerEvent): void {
    const gesto = this.gesto;
    if (!gesto || evento.pointerId !== gesto.pointerId) {
      return;
    }
    const dx = evento.clientX - gesto.inicioX;

    if (gesto.eixo === null) {
      gesto.eixo = decidirEixo(dx, evento.clientY - gesto.inicioY);
      if (gesto.eixo === 'vertical') {
        this.gesto = null;
        return;
      }
      if (gesto.eixo === null) {
        return;
      }
      this.pegar(gesto);
    }

    const agora = performance.now();
    gesto.velocidade = (evento.clientX - gesto.ultimoX) / Math.max(1, agora - gesto.ultimoT);
    gesto.ultimoX = evento.clientX;
    gesto.ultimoT = agora;
    gesto.dx = dx;

    // Um desenho por quadro da tela, por mais eventos que o dedo mande.
    gesto.pedido ??= requestAnimationFrame(() => {
      gesto.pedido = null;
      this.seguirDedo(gesto);
    });
  }

  /** A atual e as vizinhas viram quadros: o dedo pode mudar de ideia no meio. */
  private pegar(gesto: Gesto): void {
    const atual = this.visivel();
    gesto.quadros = [atual - 1, atual, atual + 1].filter((i) => i >= 0 && i < ABAS.length);
    gesto.largura = this.montarPalco(atual, gesto.quadros);
    try {
      this.palco.setPointerCapture?.(gesto.pointerId);
    } catch {
      // O ponteiro já não está ativo (o navegador assumiu o toque): o gesto
      // segue sem a captura, e o pointercancel que vem a seguir desfaz tudo.
    }
  }

  private seguirDedo(gesto: Gesto): void {
    const atual = this.visivel();
    const temVizinha = gesto.dx < 0 ? atual < ABAS.length - 1 : atual > 0;
    gesto.deslocamento = temVizinha ? gesto.dx : gesto.dx * RESISTENCIA;

    const abas = this.elementos();
    for (const indice of gesto.quadros) {
      abas[indice].style.transform = posicao(indice - atual, gesto.deslocamento);
    }
    const naBarra = atual - gesto.deslocamento / gesto.largura;
    this.pilula.mover(Math.min(ABAS.length - 1, Math.max(0, naBarra)));
  }

  private soltar(evento: PointerEvent, cancelado = false): void {
    const gesto = this.gesto;
    if (!gesto || evento.pointerId !== gesto.pointerId) {
      return;
    }
    this.gesto = null;
    if (gesto.eixo !== 'horizontal') {
      return;
    }
    if (gesto.pedido !== null) {
      cancelAnimationFrame(gesto.pedido);
    }
    this.seguirDedo(gesto);

    this.engolirClique = true;
    setTimeout(() => (this.engolirClique = false));

    const atual = this.visivel();
    const vizinha = atual + (gesto.dx < 0 ? 1 : -1);
    const troca =
      !cancelado &&
      vizinha >= 0 &&
      vizinha < ABAS.length &&
      deveTrocar(gesto.dx, gesto.largura, gesto.velocidade);

    this.enfileirar(() => this.assentar(gesto, troca ? vizinha : atual));
  }

  /** As abas terminam o movimento a partir de onde o dedo largou. */
  private async assentar(gesto: Gesto, destino: number): Promise<void> {
    const atual = this.visivel();
    this.pilula.soltar(destino);

    if (destino !== atual) {
      // A URL troca já: a pílula e o título não esperam a animação acabar.
      this.alvo = destino;
      this.titulo.setTitle(`${ABAS[destino].rotulo} · Tasky`);
      this.abaVisivel.avisar(ABAS[destino].rota);
      void this.zona.run(() => this.router.navigateByUrl(ABAS[destino].rota));
    }

    try {
      if (podeAnimar(this.palco)) {
        const andado = Math.abs(gesto.deslocamento);
        const falta = destino === atual ? andado : gesto.largura - andado;
        await this.animar(
          gesto.quadros.map((indice) => ({
            indice,
            de: posicao(indice - atual, gesto.deslocamento),
            para: posicao(indice - destino, 0),
          })),
          Math.max(140, Math.min(DURACAO, (falta / gesto.largura) * DURACAO * 1.4)),
        );
      }
    } finally {
      this.desmontarPalco(gesto.quadros);
    }
    this.concluir(destino);
  }

  // ----------------------------------------------------------------- palco

  /**
   * Mede antes de mexer em qualquer coisa: virar quadro tira a aba da página,
   * e a rolagem da página vai a zero.
   *
   * @returns a largura do palco, que é o quanto uma aba anda até sair
   */
  private montarPalco(atual: number, quadros: number[]): number {
    const abas = this.elementos();
    const pagina = paginaDe(abas[atual]).getBoundingClientRect();
    const coluna = (this.palco.parentElement ?? this.palco).getBoundingClientRect();
    this.rolagens[atual] = window.scrollY;
    const topoNaPagina = pagina.top + window.scrollY;

    this.palco.classList.add('trilha--movendo');
    this.palco.style.left = `${coluna.left}px`;
    this.palco.style.width = `${coluna.width}px`;
    for (const indice of quadros) {
      emQuadro(
        abas[indice],
        topoNaPagina - this.rolagens[indice],
        pagina.left - coluna.left,
        pagina.width,
      );
    }
    return coluna.width || window.innerWidth;
  }

  private desmontarPalco(quadros: number[]): void {
    const abas = this.elementos();
    for (const indice of quadros) {
      foraDoQuadro(abas[indice]);
    }
    this.palco.classList.remove('trilha--movendo');
    this.palco.style.left = '';
    this.palco.style.width = '';
  }

  /** A posição final fica no estilo antes de animar: ao acabar, nada pula. */
  private async animar(movimentos: Movimento[], duracao = DURACAO): Promise<void> {
    const abas = this.elementos();
    this.animacoes = movimentos.map(({ indice, de, para }) => {
      abas[indice].style.transform = para;
      return abas[indice].animate([{ transform: de }, { transform: para }], {
        duration: duracao,
        easing: CURVA,
      });
    });
    try {
      await Promise.all(this.animacoes.map((animacao) => animacao.finished));
    } finally {
      this.animacoes = [];
    }
  }

  /**
   * Tudo na mesma tarefa, sem quadro desenhado no meio: a aba nova entra na
   * página e já aparece na rolagem dela.
   */
  private concluir(para: number): void {
    this.visivel.set(para);
    this.detector.detectChanges();
    window.scrollTo(0, this.rolagens[para]);
  }

  /** Uma animação cancelada (a trilha saiu da tela no meio) não pode travar a fila. */
  private enfileirar(passo: () => Promise<void>): void {
    this.fila = this.fila.then(passo).catch(() => undefined);
  }

  private elementos(): HTMLElement[] {
    return this.abas().map((aba) => aba.nativeElement);
  }
}

/** Onde fica a aba que está `distancia` abas à direita da visível. */
function posicao(distancia: number, deslocamento: number): string {
  return `translateX(calc(${distancia * 100}% + ${deslocamento}px))`;
}

function paginaDe(aba: HTMLElement): HTMLElement {
  return aba.firstElementChild as HTMLElement;
}

/**
 * O quadro é o que se move com transform; a página dentro dele desce com `top`.
 * Com transform também na página, o botão "+" (fixed) passaria a se posicionar
 * pela página inteira, e não pela tela.
 */
function emQuadro(aba: HTMLElement, topo: number, esquerda: number, largura: number): void {
  aba.classList.add('trilha__aba--quadro');
  const pagina = paginaDe(aba);
  pagina.style.top = `${topo}px`;
  pagina.style.left = `${esquerda}px`;
  pagina.style.width = `${largura}px`;
}

function foraDoQuadro(aba: HTMLElement): void {
  aba.classList.remove('trilha__aba--quadro');
  aba.style.transform = '';
  const pagina = paginaDe(aba);
  pagina.style.top = '';
  pagina.style.left = '';
  pagina.style.width = '';
}

function podeAnimar(elemento: HTMLElement): boolean {
  return (
    typeof elemento.animate === 'function' &&
    !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  );
}
