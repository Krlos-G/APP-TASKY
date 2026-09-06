import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { DiaService } from '../../core/rotina/dia.service';
import { TimeProvider } from '../../core/tempo/time-provider.service';
import { Bloco, Dia } from '../../core/rotina/rotina.models';
import { RespostaErro } from '../../core/auth/auth.models';

/** Onde o momento atual cai em relação aos blocos do dia. */
type Situacao = 'antes' | 'durante' | 'entre' | 'depois' | 'sem-blocos';

interface Agora {
  situacao: Situacao;
  atual: Bloco | null;
  proximo: Bloco | null;
  /** Minutos até o fim do bloco atual, ou até o começo do próximo. */
  minutosRestantes: number;
}

@Component({
  imports: [RouterLink],
  selector: 'app-hoje',
  styleUrl: './hoje.component.scss',
  templateUrl: './hoje.component.html',
})
export class Hoje implements OnInit {
  private readonly diaService = inject(DiaService);
  private readonly tempo = inject(TimeProvider);

  protected readonly dia = signal<Dia | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  /**
   * Recalculado a cada minuto, porque depende do sinal do TimeProvider.
   *
   * É o que faz a contagem regressiva andar sozinha sem nenhum código de
   * atualização na tela.
   */
  protected readonly agora = computed<Agora>(() => {
    // Leitura explícita: é ela que amarra este computed ao relógio.
    this.tempo.agora();

    const blocos = this.dia()?.blocos ?? [];
    if (blocos.length === 0) {
      return { situacao: 'sem-blocos', atual: null, proximo: null, minutosRestantes: 0 };
    }

    const minutos = this.tempo.minutosDoDia();
    const atual = blocos.find(
      (b) => minutos >= this.emMinutos(b.horaInicio) && minutos < this.emMinutos(b.horaFim),
    );
    const proximo = blocos.find((b) => this.emMinutos(b.horaInicio) > minutos);

    if (atual) {
      return {
        situacao: 'durante',
        atual,
        proximo: proximo ?? null,
        minutosRestantes: this.emMinutos(atual.horaFim) - minutos,
      };
    }

    if (proximo) {
      // Antes do primeiro bloco do dia, ou num intervalo entre dois.
      const situacao: Situacao =
        minutos < this.emMinutos(blocos[0].horaInicio) ? 'antes' : 'entre';
      return {
        situacao,
        atual: null,
        proximo,
        minutosRestantes: this.emMinutos(proximo.horaInicio) - minutos,
      };
    }

    return { situacao: 'depois', atual: null, proximo: null, minutosRestantes: 0 };
  });

  protected readonly dataPorExtenso = computed(() => {
    const data = this.dia()?.data;
    if (!data) {
      return '';
    }
    // A data vem como yyyy-MM-dd; montar com números evita o parse UTC do
    // Date, que jogaria o dia para trás em fusos negativos.
    const [ano, mes, dia] = data.split('-').map(Number);
    return new Date(ano, mes - 1, dia).toLocaleDateString('pt-BR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    });
  });

  ngOnInit(): void {
    this.diaService.buscar().subscribe({
      next: (dia) => {
        this.dia.set(dia);
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregando.set(false);
        const corpo = falha.error as RespostaErro | null;
        this.erro.set(corpo?.erro ?? 'Não foi possível carregar o dia.');
      },
    });
  }

  /** O bloco está acontecendo agora? Usado para destacá-lo na linha do tempo. */
  protected ehAtual(bloco: Bloco): boolean {
    return this.agora().atual?.id === bloco.id;
  }

  protected jaPassou(bloco: Bloco): boolean {
    this.tempo.agora();
    return this.tempo.minutosDoDia() >= this.emMinutos(bloco.horaFim);
  }

  protected hhmm(hora: string): string {
    return hora.slice(0, 5);
  }

  /** "1h12" lê melhor que "72 minutos" para uma contagem regressiva. */
  protected duracao(minutos: number): string {
    if (minutos < 60) {
      return `${minutos} min`;
    }
    const horas = Math.floor(minutos / 60);
    const resto = minutos % 60;
    return resto === 0 ? `${horas}h` : `${horas}h${String(resto).padStart(2, '0')}`;
  }

  private emMinutos(hora: string): number {
    const [h, m] = hora.split(':').map(Number);
    return h * 60 + m;
  }
}
