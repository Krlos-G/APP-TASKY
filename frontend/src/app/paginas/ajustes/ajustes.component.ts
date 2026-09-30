import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { take } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ChavePush, PushService, TesteEnvio } from '../../core/push/push.service';
import { Perfil, UsuarioService } from '../../core/usuario/usuario.service';
import { RespostaErro } from '../../core/auth/auth.models';
import { Icone } from '../../core/ui/icone.component';

@Component({
  imports: [Icone],
  selector: 'app-ajustes',
  styleUrl: './ajustes.component.scss',
  templateUrl: './ajustes.component.html',
})
export class Ajustes implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly pushService = inject(PushService);
  private readonly usuarioService = inject(UsuarioService);
  private readonly swPush = inject(SwPush);
  private readonly router = inject(Router);

  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly chave = signal<ChavePush | null>(null);
  protected readonly inscricao = signal<PushSubscription | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly inscrito = computed(() => this.inscricao() !== null);

  /** O navegador só expõe push com o service worker registrado. */
  protected readonly suportado = this.swPush.isEnabled;

  /**
   * No iPhone, Web Push só funciona com o app na tela de início. Sem este
   * aviso, o botão simplesmente não faz nada e ninguém entende por quê.
   */
  protected readonly precisaInstalar = ehIphone() && !estaInstalado();

  ngOnInit(): void {
    this.usuarioService.perfil().subscribe({
      next: (perfil) => this.perfil.set(perfil),
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });

    this.pushService.chave().subscribe({
      next: (chave) => this.chave.set(chave),
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });

    if (this.suportado) {
      this.swPush.subscription.pipe(take(1)).subscribe((inscricao) => {
        // O navegador ter uma inscricao nao prova que o servidor sabe dela: o
        // registro pode ter falhado. Reenviar e barato, idempotente, e evita a
        // tela afirmar que os lembretes chegam quando nao chegam.
        if (inscricao) {
          this.registrar(inscricao, { silencioso: true });
        }
      });
    }
  }

  protected ativar(): void {
    const chave = this.chave();
    if (!chave?.habilitado || this.ocupado()) {
      return;
    }

    this.ocupado.set(true);
    this.swPush
      .requestSubscription({ serverPublicKey: chave.chavePublica })
      .then((inscricao) => this.registrar(inscricao))
      .catch(() => {
        this.ocupado.set(false);
        this.erro.set(
          'Não foi possível ativar. Confira se você permitiu notificações para este site.',
        );
      });
  }

  protected desativar(): void {
    const atual = this.inscricao();
    if (!atual || this.ocupado()) {
      return;
    }

    this.ocupado.set(true);
    // Primeiro o servidor esquece o aparelho; depois o navegador.
    this.pushService.remover(atual.endpoint).subscribe({
      next: () => {
        this.swPush.unsubscribe().catch(() => undefined);
        this.inscricao.set(null);
        this.concluir('Notificações desativadas neste aparelho.');
      },
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  protected testar(): void {
    this.ocupado.set(true);
    this.pushService.testar().subscribe({
      next: (resultado) => this.concluir(mensagemDoTeste(resultado)),
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  protected definirResumo(hora: string): void {
    this.salvarResumo(hora || null);
  }

  protected desligarResumo(): void {
    this.salvarResumo(null);
  }

  protected sair(): void {
    this.auth.sair().subscribe({
      next: () => this.irParaLogin(),
      error: () => this.irParaLogin(),
    });
  }

  // ------------------------------------------------------------------ apoio

  private registrar(inscricao: PushSubscription, opcoes = { silencioso: false }): void {
    const chaves = inscricao.toJSON().keys ?? {};

    this.pushService
      .registrar({
        endpoint: inscricao.endpoint,
        p256dh: chaves['p256dh'] ?? '',
        auth: chaves['auth'] ?? '',
        userAgent: navigator.userAgent,
      })
      .subscribe({
        next: () => {
          this.inscricao.set(inscricao);
          if (!opcoes.silencioso) {
            this.concluir('Notificações ativadas neste aparelho.');
          }
        },
        // Falhando a sincronizacao de fundo, a tela volta a oferecer "ativar"
        // em vez de prometer o que nao vai acontecer.
        error: (falha: HttpErrorResponse) => {
          this.inscricao.set(null);
          if (!opcoes.silencioso) {
            this.falhar(falha);
          }
        },
      });
  }

  private salvarResumo(hora: string | null): void {
    this.ocupado.set(true);
    this.usuarioService.definirResumoDiario(hora).subscribe({
      next: () => {
        this.perfil.update((atual) => (atual ? { ...atual, horaResumoDiario: hora } : atual));
        this.concluir(hora ? `Resumo diário às ${hora}.` : 'Resumo diário desligado.');
      },
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  private concluir(aviso: string): void {
    this.ocupado.set(false);
    this.erro.set(null);
    this.aviso.set(aviso);
  }

  private falhar(falha: HttpErrorResponse): void {
    this.ocupado.set(false);
    this.aviso.set(null);
    const corpo = falha.error as RespostaErro | null;
    this.erro.set(corpo?.erro ?? 'Algo deu errado. Tente novamente.');
  }

  private irParaLogin(): void {
    void this.router.navigateByUrl('/login');
  }
}

/** O servidor distingue "nenhum aparelho" de "os aparelhos nao receberam". */
function mensagemDoTeste(resultado: TesteEnvio): string {
  if (resultado.enviadas > 0) {
    return `Enviado para ${resultado.enviadas} de ${resultado.aparelhos} aparelho(s).`;
  }
  if (resultado.aparelhos === 0) {
    return 'Este aparelho não está registrado no servidor. Ative as notificações de novo.';
  }
  return 'O servidor não conseguiu entregar. Tente ativar de novo neste aparelho.';
}

function ehIphone(): boolean {
  return /iPhone|iPad|iPod/.test(navigator.userAgent);
}

function estaInstalado(): boolean {
  const comoApp = window.matchMedia?.('(display-mode: standalone)').matches ?? false;
  // O Safari do iOS não implementa display-mode e usa esta propriedade própria.
  const noIos = (navigator as { standalone?: boolean }).standalone === true;
  return comoApp || noIos;
}
