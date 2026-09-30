import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * O lugar do conteudo enquanto ele carrega, na forma que ele vai ter.
 *
 * `lista` imita um cartao de linhas com circulo (hoje, tarefas); `cartoes`
 * imita cartoes soltos (resumo, habitos, rotina, formularios).
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-esqueleto',
  template: `
    <div class="esqueleto" aria-busy="true">
      <span class="so-leitor">Carregando…</span>
      @if (tipo() === 'lista') {
        <div class="esqueleto__cartao">
          @for (item of itens(); track $index) {
            <div class="esqueleto__item">
              <span class="esqueleto__circulo"></span>
              <span class="esqueleto__textos">
                <span class="esqueleto__linha"></span>
                <span class="esqueleto__linha esqueleto__linha--curta"></span>
              </span>
            </div>
          }
        </div>
      } @else {
        @for (item of itens(); track $index) {
          <div class="esqueleto__cartao">
            <span class="esqueleto__linha esqueleto__linha--curta"></span>
            <span class="esqueleto__linha"></span>
          </div>
        }
      }
    </div>
  `,
})
export class Esqueleto {
  readonly tipo = input<'lista' | 'cartoes'>('lista');
  readonly quantidade = input(3);

  protected readonly itens = computed(() => Array.from({ length: this.quantidade() }));
}
