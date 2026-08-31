import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

interface ItemNavegacao {
  rota: string;
  rotulo: string;
  icone: string;
}

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.component.scss',
  templateUrl: './app.component.html',
})
export class App {
  /**
   * Um unico conjunto de itens alimenta as duas formas de navegacao
   * (tab bar no celular, lateral no desktop). Quem decide o formato e o CSS,
   * sem duplicar markup.
   */
  protected readonly itens: ItemNavegacao[] = [
    { rota: '/hoje', rotulo: 'Hoje', icone: '📅' },
    { rota: '/resumo', rotulo: 'Resumo', icone: '📊' },
    { rota: '/habitos', rotulo: 'Hábitos', icone: '🔥' },
    { rota: '/tarefas', rotulo: 'Tarefas', icone: '✓' },
  ];
}
