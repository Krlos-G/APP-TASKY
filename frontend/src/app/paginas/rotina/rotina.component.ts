import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RotinaService } from '../../core/rotina/rotina.service';
import {
  DIAS_SEMANA,
  DiaSemana,
  ModeloDia,
  NOME_DO_DIA,
  Semana,
} from '../../core/rotina/rotina.models';
import { RespostaErro } from '../../core/auth/auth.models';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-rotina',
  styleUrl: './rotina.component.scss',
  templateUrl: './rotina.component.html',
})
export class Rotina implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly rotinaService = inject(RotinaService);

  protected readonly diasSemana = DIAS_SEMANA;
  protected readonly nomeDoDia = NOME_DO_DIA;

  protected readonly modelos = signal<ModeloDia[]>([]);
  protected readonly semana = signal<Semana | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  /** Modelo aberto para edição dos blocos. Nulo = nenhum expandido. */
  protected readonly modeloAberto = signal<number | null>(null);

  /** Bloco em edição; nulo significa que o formulário está criando um novo. */
  protected readonly blocoEmEdicao = signal<number | null>(null);

  protected readonly semModelos = computed(() => this.modelos().length === 0);

  protected readonly formModelo = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(100)]],
  });

  protected readonly formBloco = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(200)]],
    horaInicio: ['09:00', [Validators.required]],
    horaFim: ['10:00', [Validators.required]],
    cor: ['#4f46e5'],
  });

  ngOnInit(): void {
    this.carregar();
  }

  private carregar(): void {
    this.carregando.set(true);
    this.rotinaService.listarModelos().subscribe({
      next: (modelos) => {
        this.modelos.set(modelos);
        this.rotinaService.verSemana().subscribe({
          next: (semana) => {
            this.semana.set(semana);
            this.carregando.set(false);
          },
          error: (e) => this.falhar(e),
        });
      },
      error: (e) => this.falhar(e),
    });
  }

  // ------------------------------------------------------------- modelos

  protected criarModelo(): void {
    if (this.formModelo.invalid) {
      this.formModelo.markAllAsTouched();
      return;
    }

    this.rotinaService
      .criarModelo({ nome: this.formModelo.getRawValue().nome, padrao: false })
      .subscribe({
        next: (modelo) => {
          this.modelos.update((lista) => [...lista, modelo]);
          this.formModelo.reset({ nome: '' });
          this.modeloAberto.set(modelo.id);
          this.erro.set(null);
        },
        error: (e) => this.falhar(e),
      });
  }

  protected apagarModelo(modelo: ModeloDia): void {
    this.rotinaService.apagarModelo(modelo.id).subscribe({
      next: () => {
        this.modelos.update((lista) => lista.filter((m) => m.id !== modelo.id));
        this.erro.set(null);
      },
      // O 409 traz em quais dias o modelo está em uso; mostrar isso é mais
      // útil do que um "não foi possível apagar" genérico.
      error: (e) => this.falhar(e),
    });
  }

  protected alternarModelo(id: number): void {
    this.modeloAberto.update((atual) => (atual === id ? null : id));
    this.cancelarEdicaoDeBloco();
  }

  // -------------------------------------------------------------- blocos

  protected editarBloco(modelo: ModeloDia, blocoId: number): void {
    const bloco = modelo.blocos.find((b) => b.id === blocoId);
    if (!bloco) {
      return;
    }
    this.blocoEmEdicao.set(blocoId);
    this.formBloco.setValue({
      titulo: bloco.titulo,
      horaInicio: bloco.horaInicio.slice(0, 5),
      horaFim: bloco.horaFim.slice(0, 5),
      cor: bloco.cor ?? '#4f46e5',
    });
  }

  protected cancelarEdicaoDeBloco(): void {
    this.blocoEmEdicao.set(null);
    this.formBloco.reset({ titulo: '', horaInicio: '09:00', horaFim: '10:00', cor: '#4f46e5' });
  }

  protected salvarBloco(modeloId: number): void {
    if (this.formBloco.invalid) {
      this.formBloco.markAllAsTouched();
      return;
    }

    const valores = this.formBloco.getRawValue();
    if (valores.horaFim <= valores.horaInicio) {
      // Comparação de string funciona com HH:mm por ser ordenável. O servidor
      // valida de novo — isto é só para o erro aparecer sem ida ao servidor.
      this.erro.set('A hora de fim precisa ser depois da hora de início.');
      return;
    }

    const emEdicao = this.blocoEmEdicao();
    const requisicao = emEdicao
      ? this.rotinaService.atualizarBloco(emEdicao, valores)
      : this.rotinaService.adicionarBloco(modeloId, valores);

    requisicao.subscribe({
      next: (modelo) => {
        this.substituirModelo(modelo);
        this.cancelarEdicaoDeBloco();
        this.erro.set(null);
      },
      error: (e) => this.falhar(e),
    });
  }

  protected apagarBloco(modeloId: number, blocoId: number): void {
    this.rotinaService.apagarBloco(blocoId).subscribe({
      next: () => {
        this.modelos.update((lista) =>
          lista.map((m) =>
            m.id === modeloId
              ? { ...m, blocos: m.blocos.filter((b) => b.id !== blocoId) }
              : m,
          ),
        );
        this.erro.set(null);
      },
      error: (e) => this.falhar(e),
    });
  }

  // -------------------------------------------------------------- semana

  protected modeloDoDia(dia: DiaSemana): number | null {
    return this.semana()?.modeloPorDia[dia]?.id ?? null;
  }

  protected atribuir(dia: DiaSemana, valor: string): void {
    const atual = this.semana();
    if (!atual) {
      return;
    }

    // A API substitui o conjunto, então enviamos os sete dias sempre.
    const mapa = {} as Record<DiaSemana, number | null>;
    for (const d of DIAS_SEMANA) {
      mapa[d] = d === dia ? (valor ? Number(valor) : null) : this.modeloDoDia(d);
    }

    this.rotinaService.definirSemana(mapa).subscribe({
      next: (semana) => {
        this.semana.set(semana);
        this.erro.set(null);
      },
      error: (e) => this.falhar(e),
    });
  }

  // --------------------------------------------------------------- apoio

  protected avisosDoBloco(modelo: ModeloDia, blocoId: number): string[] {
    return modelo.sobreposicoes
      .filter((s) => s.primeiroBlocoId === blocoId || s.segundoBlocoId === blocoId)
      .map((s) => s.descricao);
  }

  private substituirModelo(atualizado: ModeloDia): void {
    this.modelos.update((lista) =>
      lista.map((m) => (m.id === atualizado.id ? atualizado : m)),
    );
  }

  private falhar(falha: HttpErrorResponse): void {
    this.carregando.set(false);
    const corpo = falha.error as RespostaErro | null;
    this.erro.set(corpo?.erro ?? 'Algo deu errado. Tente novamente.');
  }
}
