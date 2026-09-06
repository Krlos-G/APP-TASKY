import { Injectable, signal } from '@angular/core';

/**
 * Fonte unica de "agora" no front.
 *
 * Existe pelo mesmo motivo do Clock injetavel no backend: sem ela, testar o
 * bloco atual da tela Hoje exigiria esperar o relogio real virar. Os testes
 * trocam esta classe por uma versao controlada.
 */
@Injectable({ providedIn: 'root' })
export class TimeProvider {
  /**
   * Sinal que muda a cada minuto.
   *
   * Um minuto e a granularidade certa: a tela mostra "faltam 1h12", que nao
   * muda mais rapido que isso. Segundo a segundo so gastaria renderizacao.
   */
  private readonly agoraAtual = signal(new Date());
  readonly agora = this.agoraAtual.asReadonly();

  private intervalo: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.iniciar();
  }

  /** Data de hoje no formato ISO (yyyy-MM-dd), como a API espera. */
  hojeIso(): string {
    const d = this.agoraAtual();
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mes}-${dia}`;
  }

  /** Minutos desde a meia-noite. Simplifica comparar com os horarios dos blocos. */
  minutosDoDia(): number {
    const d = this.agoraAtual();
    return d.getHours() * 60 + d.getMinutes();
  }

  private iniciar(): void {
    if (this.intervalo !== null) {
      return;
    }
    this.intervalo = setInterval(() => this.agoraAtual.set(new Date()), 60_000);
  }

  /** Usado pelos testes; em produção o app vive enquanto a aba vive. */
  parar(): void {
    if (this.intervalo !== null) {
      clearInterval(this.intervalo);
      this.intervalo = null;
    }
  }
}
