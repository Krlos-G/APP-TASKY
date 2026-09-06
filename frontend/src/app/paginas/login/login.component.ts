import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { RespostaErro } from '../../core/auth/auth.models';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-login',
  styleUrl: './login.component.scss',
  templateUrl: './login.component.html',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', [Validators.required]],
  });

  protected entrar(): void {
    if (this.formulario.invalid || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.erro.set(null);

    this.auth.entrar(this.formulario.getRawValue()).subscribe({
      next: () => this.irParaDestino(),
      error: (falha: HttpErrorResponse) => {
        this.enviando.set(false);
        this.erro.set(this.mensagemDe(falha));
      },
    });
  }

  /**
   * Volta para onde o usuario tentou ir antes de ser barrado pelo guard.
   * Sem returnUrl, cai na tela inicial.
   */
  private irParaDestino(): void {
    const destino = this.rota.snapshot.queryParamMap.get('returnUrl') ?? '/hoje';
    void this.router.navigateByUrl(destino);
  }

  private mensagemDe(falha: HttpErrorResponse): string {
    // 429 tem tratamento proprio: dizer "e-mail ou senha invalidos" quando o
    // problema e excesso de tentativas so confundiria quem esta tentando entrar.
    if (falha.status === 429) {
      return 'Muitas tentativas. Espere um minuto e tente de novo.';
    }
    if (falha.status === 0) {
      return 'Sem conexao com o servidor.';
    }

    const corpo = falha.error as RespostaErro | null;
    return corpo?.erro ?? 'Nao foi possivel entrar. Tente novamente.';
  }
}
