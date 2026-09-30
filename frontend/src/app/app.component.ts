import { Component, effect, inject } from '@angular/core';
import { NavigationStart, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from './core/auth/auth.service';
import { Icone } from './core/ui/icone.component';
import { ABAS, direcaoEntre } from './core/ui/abas';
import { BarraDeslizavel } from './core/ui/barra-deslizavel.directive';
import { DeslizarEntreAbas } from './core/ui/deslizar-entre-abas.directive';
import { UsuarioService } from './core/usuario/usuario.service';

@Component({
  imports: [BarraDeslizavel, DeslizarEntreAbas, Icone, RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.component.scss',
  templateUrl: './app.component.html',
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly usuarioService = inject(UsuarioService);
  private readonly router = inject(Router);

  private fusoSincronizado = false;

  /** Sem sessao a navegacao some: a tela de login ocupa a pagina inteira. */
  protected readonly autenticado = this.auth.autenticado;
  protected readonly usuario = this.auth.usuario;

  /**
   * Um unico conjunto de itens alimenta as duas formas de navegacao
   * (tab bar no celular, lateral no desktop). Quem decide o formato e o CSS,
   * sem duplicar markup.
   */
  protected readonly itens = ABAS;

  constructor() {
    // A tela nova entra pelo lado da aba dela - marcado no começo de toda
    // navegação, venha ela de um toque, do arrasto na barra ou do deslizar.
    this.router.events.pipe(takeUntilDestroyed()).subscribe((evento) => {
      if (!(evento instanceof NavigationStart)) {
        return;
      }
      const direcao = direcaoEntre(this.router.url, evento.url);
      if (direcao) {
        document.documentElement.dataset['direcao'] = direcao;
      } else {
        delete document.documentElement.dataset['direcao'];
      }
    });

    // O servidor precisa do fuso gravado para disparar lembrete quando
    // ninguem esta com o app aberto - por isso o aparelho conta o dele a cada
    // sessao, em vez de a tela perguntar.
    effect(() => {
      if (!this.autenticado()) {
        this.fusoSincronizado = false;
        return;
      }
      if (this.fusoSincronizado) {
        return;
      }
      this.fusoSincronizado = true;

      this.usuarioService
        .definirFuso(Intl.DateTimeFormat().resolvedOptions().timeZone)
        // Sincronizacao de fundo: falhar aqui nao pode virar aviso na tela.
        .subscribe({ error: () => undefined });
    });
  }

  protected sair(): void {
    // Navega em qualquer desfecho: se o servidor recusou, a sessao local ja
    // foi limpa e insistir na tela interna nao ajudaria o usuario.
    this.auth.sair().subscribe({
      next: () => this.irParaLogin(),
      error: () => this.irParaLogin(),
    });
  }

  private irParaLogin(): void {
    void this.router.navigateByUrl('/login');
  }
}
