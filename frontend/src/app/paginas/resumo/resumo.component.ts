import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ResumoService } from '../../core/resumo/resumo.service';
import { Resumo as ResumoDoDia } from '../../core/resumo/resumo.models';
import { dataDeIso, formatarDataPorExtenso, formatarStreak } from '../../core/tempo/formatos';
import { RespostaErro } from '../../core/auth/auth.models';

interface Progresso {
  feitos: number;
  total: number;
  porcento: number;
}

@Component({
  imports: [RouterLink],
  selector: 'app-resumo',
  styleUrl: './resumo.component.scss',
  templateUrl: './resumo.component.html',
})
export class Resumo implements OnInit {
  private readonly resumoService = inject(ResumoService);

  protected readonly resumo = signal<ResumoDoDia | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  protected readonly streakPorExtenso = formatarStreak;

  protected readonly dataPorExtenso = computed(() => {
    const data = this.resumo()?.data;
    return data ? formatarDataPorExtenso(data) : '';
  });

  protected readonly periodo = computed(() => {
    const semana = this.resumo()?.semana;
    return semana ? periodoPorExtenso(semana.inicio, semana.fim) : '';
  });

  protected readonly progressoDoDia = computed<Progresso | null>(() => {
    const dia = this.resumo()?.dia;
    if (!dia) {
      return null;
    }
    return progresso(dia.habitosFeitos + dia.tarefasFeitas, dia.habitosDevidos + dia.tarefasDoDia);
  });

  protected readonly progressoDaSemana = computed<Progresso | null>(() => {
    const semana = this.resumo()?.semana;
    return semana ? progresso(semana.habitosFeitos, semana.habitosCobrados) : null;
  });

  ngOnInit(): void {
    this.resumoService.ver().subscribe({
      next: (resumo) => {
        this.resumo.set(resumo);
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregando.set(false);
        const corpo = falha.error as RespostaErro | null;
        this.erro.set(corpo?.erro ?? 'Não foi possível carregar o resumo.');
      },
    });
  }
}

/** Nulo quando não há o que contar: melhor esconder a barra do que mostrar 0%. */
function progresso(feitos: number, total: number): Progresso | null {
  if (total === 0) {
    return null;
  }
  return { feitos, total, porcento: Math.round((feitos / total) * 100) };
}

function periodoPorExtenso(inicioIso: string, fimIso: string): string {
  const inicio = dataDeIso(inicioIso);
  const fim = dataDeIso(fimIso);
  const mes = (data: Date) => data.toLocaleDateString('pt-BR', { month: 'long' });

  if (inicioIso === fimIso) {
    return `${inicio.getDate()} de ${mes(inicio)}`;
  }
  if (inicio.getMonth() === fim.getMonth()) {
    return `${inicio.getDate()} a ${fim.getDate()} de ${mes(fim)}`;
  }
  return `${inicio.getDate()} de ${mes(inicio)} a ${fim.getDate()} de ${mes(fim)}`;
}
