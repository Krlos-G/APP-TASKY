import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, switchMap, tap } from 'rxjs';
import { TarefaService } from '../../core/tarefas/tarefa.service';
import { FILTROS_TAREFA, FiltroTarefa, Tarefa } from '../../core/tarefas/tarefa.models';
import { formatarDataCurta, formatarDuracao } from '../../core/tempo/formatos';
import { RespostaErro } from '../../core/auth/auth.models';

@Component({
  imports: [RouterLink],
  selector: 'app-tarefas',
  styleUrl: './tarefas.component.scss',
  templateUrl: './tarefas.component.html',
})
export class Tarefas {
  private readonly tarefaService = inject(TarefaService);
  private readonly rota = inject(ActivatedRoute);

  protected readonly filtros = FILTROS_TAREFA;
  protected readonly filtro = signal<FiltroTarefa>('HOJE');
  protected readonly tarefas = signal<Tarefa[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  protected readonly mensagemVazia = computed(
    () => FILTROS_TAREFA.find((f) => f.valor === this.filtro())?.vazio ?? '',
  );

  constructor() {
    // O filtro mora na URL: voltar da edição reabre a aba em que se estava.
    // O switchMap descarta a resposta de uma aba que já foi trocada.
    this.rota.queryParamMap
      .pipe(
        map((parametros) => filtroValido(parametros.get('filtro'))),
        tap((filtro) => {
          this.filtro.set(filtro);
          this.carregando.set(true);
        }),
        switchMap((filtro) =>
          this.tarefaService.listar(filtro).pipe(
            catchError((falha: HttpErrorResponse) => {
              this.falhar(falha);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((tarefas) => {
        if (tarefas) {
          this.tarefas.set(tarefas);
          this.erro.set(null);
        }
        this.carregando.set(false);
      });
  }

  /** A tarefa fica no lugar depois do toque: nada some da aba, e o engano se desfaz. */
  protected alternar(tarefa: Tarefa): void {
    const requisicao =
      tarefa.status === 'FEITA'
        ? this.tarefaService.desfazerConclusao(tarefa.id)
        : this.tarefaService.concluir(tarefa.id);

    requisicao.subscribe({
      next: (atualizada) =>
        this.tarefas.update((lista) =>
          lista.map((t) => (t.id === atualizada.id ? atualizada : t)),
        ),
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  protected detalhe(tarefa: Tarefa): string {
    const partes: string[] = [];

    const data =
      tarefa.dataPlanejada && this.filtro() !== 'HOJE'
        ? formatarDataCurta(tarefa.dataPlanejada)
        : null;
    const quando = [data, tarefa.horaPlanejada?.slice(0, 5)].filter(Boolean).join(' ');
    if (quando) {
      partes.push(quando);
    }
    if (tarefa.minutosEstimados) {
      partes.push(formatarDuracao(tarefa.minutosEstimados));
    }
    if (tarefa.prioridade === 'ALTA') {
      partes.push('prioridade alta');
    }
    if (tarefa.dataLimite && !tarefa.vencida && tarefa.status === 'A_FAZER') {
      partes.push(`prazo ${formatarDataCurta(tarefa.dataLimite)}`);
    }

    return partes.join(' · ');
  }

  private falhar(falha: HttpErrorResponse): void {
    const corpo = falha.error as RespostaErro | null;
    this.erro.set(corpo?.erro ?? 'Algo deu errado. Tente novamente.');
  }
}

function filtroValido(valor: string | null): FiltroTarefa {
  return FILTROS_TAREFA.some((f) => f.valor === valor) ? (valor as FiltroTarefa) : 'HOJE';
}
