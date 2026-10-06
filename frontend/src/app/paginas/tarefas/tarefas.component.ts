import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { TarefaService } from '../../core/tarefas/tarefa.service';
import { Tarefa } from '../../core/tarefas/tarefa.models';
import { agruparPorQuando } from '../../core/tarefas/agrupar';
import { formatarDataCurta, formatarDuracao } from '../../core/tempo/formatos';
import { TimeProvider } from '../../core/tempo/time-provider.service';
import { RespostaErro } from '../../core/auth/auth.models';
import { Icone } from '../../core/ui/icone.component';
import { Esqueleto } from '../../core/ui/esqueleto.component';
import { AbaVisivel } from '../../core/ui/aba-visivel';

/**
 * Todas as tarefas por fazer numa lista só, por quando - em vez de cinco
 * filtros, em que o de hoje abria quase sempre vazio.
 */
@Component({
  imports: [Esqueleto, Icone, NgTemplateOutlet, RouterLink],
  selector: 'app-tarefas',
  styleUrl: './tarefas.component.scss',
  templateUrl: './tarefas.component.html',
})
export class Tarefas {
  private readonly tarefaService = inject(TarefaService);
  private readonly tempo = inject(TimeProvider);

  protected readonly pendentes = signal<Tarefa[]>([]);
  protected readonly concluidas = signal<Tarefa[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly mostrandoConcluidas = signal(false);
  /**
   * As concluídas só viram lista na primeira vez que se abre - elas acumulam
   * com o tempo. Depois ficam, para fechar também deslizar.
   */
  protected readonly concluidasJaAbertas = signal(false);

  protected readonly secoes = computed(() =>
    agruparPorQuando(this.pendentes(), this.tempo.hojeIso()),
  );

  constructor() {
    this.carregar();
    inject(AbaVisivel)
      .voltou('/tarefas')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.carregar({ silencioso: true }));
  }

  protected alternarConcluidas(): void {
    this.concluidasJaAbertas.set(true);
    this.mostrandoConcluidas.update((aberta) => !aberta);
  }

  /** A tarefa fica no lugar depois do toque: nada some da lista, e o engano se desfaz. */
  protected alternar(tarefa: Tarefa): void {
    const requisicao =
      tarefa.status === 'FEITA'
        ? this.tarefaService.desfazerConclusao(tarefa.id)
        : this.tarefaService.concluir(tarefa.id);

    requisicao.subscribe({
      next: (atualizada) => {
        const trocar = (lista: Tarefa[]) =>
          lista.map((t) => (t.id === atualizada.id ? atualizada : t));
        this.pendentes.update(trocar);
        this.concluidas.update(trocar);
      },
      error: (falha: HttpErrorResponse) => this.falhar(falha),
    });
  }

  protected detalhe(tarefa: Tarefa): string {
    const partes: string[] = [];

    // O dia já está no título da seção; só a atrasada diz de quando era.
    const atrasada =
      tarefa.status === 'A_FAZER' &&
      tarefa.dataPlanejada !== null &&
      tarefa.dataPlanejada < this.tempo.hojeIso();
    if (atrasada) {
      partes.push(`de ${formatarDataCurta(tarefa.dataPlanejada!)}`);
    }
    if (tarefa.horaPlanejada) {
      partes.push(tarefa.horaPlanejada.slice(0, 5));
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

  /** Silencioso: a tela segue mostrando o que tinha, e uma falha não a troca por um erro. */
  private carregar({ silencioso = false } = {}): void {
    forkJoin([
      this.tarefaService.listar('PENDENTES'),
      this.tarefaService.listar('CONCLUIDAS'),
    ]).subscribe({
      next: ([pendentes, concluidas]) => {
        this.pendentes.set(pendentes);
        this.concluidas.set(concluidas);
        this.carregando.set(false);
        this.erro.set(null);
      },
      error: (falha: HttpErrorResponse) => {
        if (silencioso) {
          return;
        }
        this.carregando.set(false);
        this.falhar(falha);
      },
    });
  }

  private falhar(falha: HttpErrorResponse): void {
    const corpo = falha.error as RespostaErro | null;
    this.erro.set(corpo?.erro ?? 'Algo deu errado. Tente novamente.');
  }
}
