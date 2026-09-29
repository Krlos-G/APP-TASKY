import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type NomeIcone =
  | 'hoje'
  | 'resumo'
  | 'habitos'
  | 'tarefas'
  | 'rotina'
  | 'ajustes'
  | 'check'
  | 'mais'
  | 'menos'
  | 'lapis'
  | 'lixeira'
  | 'sino'
  | 'relogio'
  | 'chevron';

/**
 * Desenhados na geometria dos SF Symbols - grade de 24, traco 1.75, pontas
 * redondas - porque a fonte da Apple nao e licenciada para web, e uma
 * biblioteca inteira seria dependencia nova para uma duzia de icones.
 */
const CAMINHOS: Record<NomeIcone, string[]> = {
  hoje: [
    'M7.5 5h9a4.5 4.5 0 0 1 4.5 4.5v7a4.5 4.5 0 0 1-4.5 4.5h-9A4.5 4.5 0 0 1 3 16.5v-7A4.5 4.5 0 0 1 7.5 5z',
    'M8 3v4',
    'M16 3v4',
    'M3 10h18',
  ],
  resumo: ['M6 19v-6', 'M12 19V6', 'M18 19v-9'],
  habitos: [
    'M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4.1 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z',
  ],
  tarefas: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z', 'M8.4 12.2l2.5 2.5 4.7-5.1'],
  rotina: ['M5 7h11', 'M5 12h14', 'M5 17h7'],
  ajustes: [
    'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z',
    'M19.6 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.3a2 2 0 1 1-4 0v-.2a1.6 1.6 0 0 0-2.7-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3 15H2.8a2 2 0 1 1 0-4H3a1.6 1.6 0 0 0 1.1-2.7L4 8.2a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9.6 4.4V4.2a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.3a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.7 1z',
  ],
  check: ['M5 12.5l4.6 4.6L19 7.2'],
  mais: ['M12 5v14', 'M5 12h14'],
  menos: ['M5 12h14'],
  lapis: ['M4.5 19.5l.9-3.6L16.8 4.5a2 2 0 0 1 2.8 2.8L8.1 18.6l-3.6.9z', 'M15.4 5.9l2.7 2.7'],
  lixeira: [
    'M4 7h16',
    'M9.5 7V5.5A1.5 1.5 0 0 1 11 4h2a1.5 1.5 0 0 1 1.5 1.5V7',
    'M6.5 7l.8 11.2A2 2 0 0 0 9.3 20h5.4a2 2 0 0 0 2-1.8L17.5 7',
  ],
  sino: ['M18 9a6 6 0 1 0-12 0c0 5-2 6-2 6h16s-2-1-2-6z', 'M13.7 20a2 2 0 0 1-3.4 0'],
  chevron: ['M9 6l6 6-6 6'],
  relogio: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z', 'M12 7.5V12l3 2'],
};

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-icone',
  styles: ':host { display: inline-flex }',
  template: `
    <svg
      [attr.width]="tamanho()"
      [attr.height]="tamanho()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.75"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @for (caminho of caminhos(); track caminho) {
        <path [attr.d]="caminho" />
      }
    </svg>
  `,
})
export class Icone {
  readonly nome = input.required<NomeIcone>();
  readonly tamanho = input(24);

  protected readonly caminhos = computed(() => CAMINHOS[this.nome()]);
}
