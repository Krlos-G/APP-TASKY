import { ChangeDetectorRef, Component, ElementRef, inject, signal, viewChildren } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { AbaVisivel } from '../../core/ui/aba-visivel';
import { ABAS, indiceDaAba } from '../../core/ui/abas';
import { Habitos } from '../habitos/habitos.component';
import { Hoje } from '../hoje/hoje.component';
import { Resumo } from '../resumo/resumo.component';
import { Tarefas } from '../tarefas/tarefas.component';

/** A curva do iOS, a mesma de `--curva`. */
const CURVA = 'cubic-bezier(0.32, 0.72, 0, 1)';
const DURACAO = 320;

/**
 * As quatro abas montadas lado a lado, como num app nativo: trocar de aba não
 * destrói uma tela para criar outra, e por isso não busca tudo de novo.
 *
 * Em repouso, só a aba visível está na página, e a rolagem é a da página
 * inteira — é o que mantém o "toque no relógio sobe ao topo" do iPhone. Só
 * durante o movimento as duas abas viram quadros fixos lado a lado.
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
  private readonly detector = inject(ChangeDetectorRef);
  private readonly palco = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly abas = viewChildren<ElementRef<HTMLElement>>('aba');

  protected readonly visivel = signal(-1);

  private readonly rolagens = ABAS.map(() => 0);
  /** Para onde a URL já mandou, mesmo com uma troca ainda na fila. */
  private alvo = -1;
  private animacoes: Animation[] = [];
  private fila = Promise.resolve();

  constructor() {
    inject(ActivatedRoute)
      .url.pipe(takeUntilDestroyed())
      .subscribe((segmentos) => this.abrir(indiceDaAba('/' + (segmentos[0]?.path ?? ''))));
  }

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
    // Uma animação cancelada (a trilha saiu da tela no meio) não pode travar a fila.
    this.fila = this.fila.then(() => this.trocar(destino)).catch(() => undefined);
  }

  private async trocar(para: number): Promise<void> {
    const de = this.visivel();
    if (de === para) {
      return;
    }
    this.rolagens[de] = window.scrollY;

    const abas = this.abas().map((aba) => aba.nativeElement);
    if (podeAnimar(abas[de])) {
      await this.deslizar(abas[de], abas[para], para > de ? 1 : -1, this.rolagens[para]);
    }

    // Tudo na mesma tarefa, sem quadro desenhado no meio: a aba nova entra na
    // página e já aparece na rolagem dela.
    this.visivel.set(para);
    this.detector.detectChanges();
    window.scrollTo(0, this.rolagens[para]);
  }

  private async deslizar(
    atual: HTMLElement,
    proxima: HTMLElement,
    sentido: 1 | -1,
    rolagemDaProxima: number,
  ): Promise<void> {
    // Medir antes de qualquer mudança: virar quadro tira a aba da página, e a
    // rolagem da página vai a zero.
    const pagina = paginaDe(atual).getBoundingClientRect();
    const coluna = (this.palco.parentElement ?? this.palco).getBoundingClientRect();
    const topoNaPagina = pagina.top + window.scrollY;
    const esquerda = pagina.left - coluna.left;

    this.palco.classList.add('trilha--movendo');
    this.palco.style.left = `${coluna.left}px`;
    this.palco.style.width = `${coluna.width}px`;
    emQuadro(atual, pagina.top, esquerda, pagina.width);
    emQuadro(proxima, topoNaPagina - rolagemDaProxima, esquerda, pagina.width);

    const opcoes: KeyframeAnimationOptions = { duration: DURACAO, easing: CURVA };
    this.animacoes = [
      atual.animate(
        [{ transform: 'translateX(0)' }, { transform: `translateX(${-sentido * 100}%)` }],
        opcoes,
      ),
      proxima.animate(
        [{ transform: `translateX(${sentido * 100}%)` }, { transform: 'translateX(0)' }],
        opcoes,
      ),
    ];
    try {
      await Promise.all(this.animacoes.map((animacao) => animacao.finished));
    } finally {
      this.animacoes = [];
      foraDoQuadro(atual);
      foraDoQuadro(proxima);
      this.palco.classList.remove('trilha--movendo');
      this.palco.style.left = '';
      this.palco.style.width = '';
    }
  }
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
