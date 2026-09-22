import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TarefaService } from '../../core/tarefas/tarefa.service';
import { Prioridade, TarefaRequest } from '../../core/tarefas/tarefa.models';
import { RespostaErro } from '../../core/auth/auth.models';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-tarefa-edicao',
  styleUrl: './tarefa-edicao.component.scss',
  templateUrl: './tarefa-edicao.component.html',
})
export class TarefaEdicao implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly tarefaService = inject(TarefaService);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly id = signal<number | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly confirmandoExclusao = signal(false);

  protected readonly editando = computed(() => this.id() !== null);

  /** Para onde voltar ao salvar, cancelar ou excluir. */
  private origem = '/tarefas';

  protected readonly form = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(200)]],
    observacoes: [''],
    prioridade: ['MEDIA' as Prioridade],
    minutosEstimados: [''],
    dataPlanejada: [''],
    horaPlanejada: [''],
    dataLimite: [''],
  });

  constructor() {
    // Horário sem dia não significa nada, e o servidor recusa. Em vez de
    // deixar o usuário descobrir isso no erro, o campo só abre com a data.
    this.form.controls.horaPlanejada.disable();

    this.form.controls.dataPlanejada.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((data) => {
        const hora = this.form.controls.horaPlanejada;
        if (data) {
          hora.enable({ emitEvent: false });
        } else {
          hora.setValue('', { emitEvent: false });
          hora.disable({ emitEvent: false });
        }
      });
  }

  ngOnInit(): void {
    const parametros = this.rota.snapshot.paramMap;
    const consulta = this.rota.snapshot.queryParamMap;

    this.origem = destinoSeguro(consulta.get('origem'));

    const id = parametros.get('id');
    if (id) {
      this.carregarTarefa(Number(id));
      return;
    }

    // Abrindo pelo [ + ] do Hoje, o dia já vem escolhido.
    const data = consulta.get('data');
    if (data) {
      this.form.controls.dataPlanejada.setValue(data);
    }
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const valores = this.form.getRawValue();
    const pedido: TarefaRequest = {
      titulo: valores.titulo.trim(),
      observacoes: valores.observacoes || null,
      prioridade: valores.prioridade,
      minutosEstimados: valores.minutosEstimados ? Number(valores.minutosEstimados) : null,
      dataPlanejada: valores.dataPlanejada || null,
      horaPlanejada: valores.dataPlanejada && valores.horaPlanejada ? valores.horaPlanejada : null,
      dataLimite: valores.dataLimite || null,
    };

    const id = this.id();
    const requisicao = id
      ? this.tarefaService.atualizar(id, pedido)
      : this.tarefaService.criar(pedido);

    requisicao.subscribe({
      next: () => this.voltar(),
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  protected apagar(): void {
    const id = this.id();
    if (!id) {
      return;
    }

    this.tarefaService.apagar(id).subscribe({
      next: () => this.voltar(),
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  protected voltar(): void {
    this.router.navigateByUrl(this.origem);
  }

  private carregarTarefa(id: number): void {
    this.carregando.set(true);
    this.tarefaService.buscar(id).subscribe({
      next: (tarefa) => {
        this.id.set(tarefa.id);
        this.form.patchValue({
          titulo: tarefa.titulo,
          observacoes: tarefa.observacoes ?? '',
          prioridade: tarefa.prioridade,
          minutosEstimados: tarefa.minutosEstimados ? String(tarefa.minutosEstimados) : '',
          dataPlanejada: tarefa.dataPlanejada ?? '',
          dataLimite: tarefa.dataLimite ?? '',
        });
        // Depois da data, para o campo já estar habilitado.
        this.form.controls.horaPlanejada.setValue(tarefa.horaPlanejada?.slice(0, 5) ?? '');
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  private falhar(falha: HttpErrorResponse): void {
    this.carregando.set(false);
    const corpo = falha.error as RespostaErro | null;
    this.erro.set(corpo?.erro ?? 'Algo deu errado. Tente novamente.');
  }
}

/** Só caminho interno: um "//outro.site" na URL não vira navegação para fora. */
function destinoSeguro(origem: string | null): string {
  return origem && origem.startsWith('/') && !origem.startsWith('//') ? origem : '/tarefas';
}
