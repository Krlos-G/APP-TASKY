import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import {
  catchError,
  distinctUntilChanged,
  filter,
  map,
  merge,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { TarefaService } from '../../core/tarefas/tarefa.service';
import { FILTROS_TAREFA, FiltroTarefa, Tarefa } from '../../core/tarefas/tarefa.models';
import { formatarDataCurta, formatarDuracao } from '../../core/tempo/formatos';
import { RespostaErro } from '../../core/auth/auth.models';
import { Icone } from '../../core/ui/icone.component';
import { Esqueleto } from '../../core/ui/esqueleto.component';
import { AbaVisivel } from '../../core/ui/aba-visivel';

@Component({
  imports: [Esqueleto, Icone, RouterLink],
  selector: 'app-tarefas',
  styleUrl: './tarefas.component.scss',
  templateUrl: './tarefas.component.html',
})
export class Tarefas {
  private readonly tarefaService = inject(TarefaService);
  private readonly router = inject(Router);

  protected readonly filtros = FILTROS_TAREFA;
  protected readonly filtro = signal<FiltroTarefa>('HOJE');
  protected readonly tarefas = signal<Tarefa[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  /** A aba atual viaja com o link: salvar a edição traz de volta para ela. */
  protected readonly origem = computed(() => `/tarefas?filtro=${this.filtro()}`);

  protected readonly mensagemVazia = computed(
    () => FILTROS_TAREFA.find((f) => f.valor === this.filtro())?.vazio ?? '',
  );

  constructor() {
    // O filtro mora na URL: voltar da edição reabre a aba em que se estava.
    // Viva na trilha, a tela continua ouvindo a URL mesmo escondida - e a das
    // outras abas não tem filtro. Por isso só vale a URL que é dela. Nascendo
    // escondida, carrega o filtro padrão: deslizar até ela já mostra a lista.
    const rota = inject(ActivatedRoute).snapshot;
    const filtroInicial = filtroValido(
      rota.url[0]?.path === 'tarefas' ? rota.queryParamMap.get('filtro') : null,
    );
    const filtroDaUrl = this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      filter(() => this.router.url.split(/[?#]/)[0] === '/tarefas'),
      map(() => filtroValido(this.router.parseUrl(this.router.url).queryParamMap.get('filtro'))),
      startWith(filtroInicial),
      distinctUntilChanged(),
    );

    // O switchMap descarta a resposta de um filtro que já foi trocado.
    merge(
      filtroDaUrl.pipe(map((filtro) => ({ filtro, silencioso: false }))),
      inject(AbaVisivel)
        .voltou('/tarefas')
        .pipe(map(() => ({ filtro: this.filtro(), silencioso: true }))),
    )
      .pipe(
        tap(({ filtro, silencioso }) => {
          this.filtro.set(filtro);
          if (!silencioso) {
            this.carregando.set(true);
          }
        }),
        switchMap(({ filtro, silencioso }) =>
          this.tarefaService.listar(filtro).pipe(
            catchError((falha: HttpErrorResponse) => {
              if (!silencioso) {
                this.falhar(falha);
              }
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
