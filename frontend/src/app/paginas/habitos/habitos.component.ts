import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HabitoService } from '../../core/habitos/habito.service';
import { Habito, Marcacao, StatusHabito, TipoAgenda } from '../../core/habitos/habito.models';
import { DIAS_SEMANA, DiaSemana, NOME_CURTO_DO_DIA } from '../../core/rotina/rotina.models';
import { TimeProvider } from '../../core/tempo/time-provider.service';
import { RespostaErro } from '../../core/auth/auth.models';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-habitos',
  styleUrl: './habitos.component.scss',
  templateUrl: './habitos.component.html',
})
export class Habitos implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly habitoService = inject(HabitoService);
  private readonly tempo = inject(TimeProvider);

  protected readonly diasSemana = DIAS_SEMANA;
  protected readonly nomeCurtoDoDia = NOME_CURTO_DO_DIA;

  protected readonly habitos = signal<Habito[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly mostrarArquivados = signal(false);

  protected readonly formAberto = signal(false);
  /** Nulo com o formulário aberto significa que ele está criando. */
  protected readonly emEdicao = signal<number | null>(null);

  protected readonly historicoAberto = signal<number | null>(null);
  protected readonly historico = signal<Marcacao[]>([]);
  protected readonly confirmandoExclusao = signal<number | null>(null);

  protected readonly diasEscolhidos = signal<DiaSemana[]>([]);
  protected readonly semHabitos = computed(() => this.habitos().length === 0);

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(100)]],
    tipoAgenda: ['DIARIO' as TipoAgenda],
    horaPreferida: [''],
    cor: ['#4f46e5'],
  });

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.habitoService.listar(this.mostrarArquivados()).subscribe({
      next: (habitos) => {
        this.habitos.set(habitos);
        this.carregando.set(false);
        this.erro.set(null);
      },
      error: (e) => this.falhar(e),
    });
  }

  protected alternarArquivados(): void {
    this.mostrarArquivados.update((v) => !v);
    this.carregar();
  }

  // ---------------------------------------------------------- formulário

  protected abrirNovo(): void {
    this.emEdicao.set(null);
    this.diasEscolhidos.set([]);
    this.form.reset({ nome: '', tipoAgenda: 'DIARIO', horaPreferida: '', cor: '#4f46e5' });
    this.formAberto.set(true);
  }

  protected editar(habito: Habito): void {
    this.emEdicao.set(habito.id);
    this.diasEscolhidos.set([...habito.diasSemana]);
    this.form.setValue({
      nome: habito.nome,
      tipoAgenda: habito.tipoAgenda,
      horaPreferida: habito.horaPreferida?.slice(0, 5) ?? '',
      cor: habito.cor ?? '#4f46e5',
    });
    this.formAberto.set(true);
  }

  protected fechar(): void {
    this.formAberto.set(false);
    this.emEdicao.set(null);
  }

  protected porDiasDaSemana(): boolean {
    return this.form.controls.tipoAgenda.value === 'DIAS_SEMANA';
  }

  protected diaEscolhido(dia: DiaSemana): boolean {
    return this.diasEscolhidos().includes(dia);
  }

  protected alternarDia(dia: DiaSemana): void {
    this.diasEscolhidos.update((dias) =>
      dias.includes(dia) ? dias.filter((d) => d !== dia) : [...dias, dia],
    );
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const valores = this.form.getRawValue();
    const porDias = valores.tipoAgenda === 'DIAS_SEMANA';

    if (porDias && this.diasEscolhidos().length === 0) {
      this.erro.set('Escolha ao menos um dia da semana.');
      return;
    }

    const pedido = {
      nome: valores.nome,
      tipoAgenda: valores.tipoAgenda,
      diasSemana: porDias ? this.diasEscolhidos() : [],
      horaPreferida: valores.horaPreferida || null,
      cor: valores.cor,
    };

    const id = this.emEdicao();
    const requisicao = id
      ? this.habitoService.atualizar(id, pedido)
      : this.habitoService.criar(pedido);

    requisicao.subscribe({
      next: (habito) => {
        if (id) {
          this.substituir(habito);
        } else {
          this.habitos.update((lista) => [...lista, habito].sort(porNome));
        }
        this.fechar();
        this.erro.set(null);
      },
      error: (e) => this.falhar(e),
    });
  }

  // ------------------------------------------------------------ marcação

  protected marcar(habito: Habito, status: StatusHabito): void {
    this.habitoService.marcar(habito.id, this.tempo.hojeIso(), status).subscribe({
      next: (atualizado) => this.substituir(atualizado),
      error: (e) => this.falhar(e),
    });
  }

  protected desmarcar(habito: Habito): void {
    this.habitoService.desmarcar(habito.id, this.tempo.hojeIso()).subscribe({
      next: (atualizado) => this.substituir(atualizado),
      error: (e) => this.falhar(e),
    });
  }

  // -------------------------------------------------- arquivo e exclusão

  protected alternarArquivo(habito: Habito): void {
    this.habitoService.definirArquivo(habito.id, !habito.arquivado).subscribe({
      next: () => this.carregar(),
      error: (e) => this.falhar(e),
    });
  }

  protected pedirExclusao(habito: Habito): void {
    this.confirmandoExclusao.set(habito.id);
  }

  protected cancelarExclusao(): void {
    this.confirmandoExclusao.set(null);
  }

  protected apagar(habito: Habito): void {
    this.habitoService.apagar(habito.id).subscribe({
      next: () => {
        this.habitos.update((lista) => lista.filter((h) => h.id !== habito.id));
        this.confirmandoExclusao.set(null);
        this.erro.set(null);
      },
      error: (e) => this.falhar(e),
    });
  }

  // ------------------------------------------------------------ histórico

  protected alternarHistorico(habito: Habito): void {
    if (this.historicoAberto() === habito.id) {
      this.historicoAberto.set(null);
      return;
    }

    this.historicoAberto.set(habito.id);
    this.historico.set([]);
    this.habitoService.historico(habito.id).subscribe({
      next: (marcacoes) => this.historico.set(marcacoes),
      error: (e) => this.falhar(e),
    });
  }

  // ---------------------------------------------------------------- apoio

  protected agendaPorExtenso(habito: Habito): string {
    if (habito.tipoAgenda === 'DIARIO') {
      return 'Todo dia';
    }
    return habito.diasSemana.map((d) => NOME_CURTO_DO_DIA[d]).join(', ');
  }

  protected streakPorExtenso(habito: Habito): string {
    if (habito.streak === 0) {
      return 'sem sequência';
    }
    return habito.streak === 1 ? '1 dia seguido' : `${habito.streak} dias seguidos`;
  }

  protected dataCurta(iso: string): string {
    const [, mes, dia] = iso.split('-');
    return `${dia}/${mes}`;
  }

  private substituir(atualizado: Habito): void {
    this.habitos.update((lista) =>
      lista.map((h) => (h.id === atualizado.id ? atualizado : h)),
    );
    this.erro.set(null);
  }

  private falhar(falha: HttpErrorResponse): void {
    this.carregando.set(false);
    const corpo = falha.error as RespostaErro | null;
    this.erro.set(corpo?.erro ?? 'Algo deu errado. Tente novamente.');
  }
}

const porNome = (a: Habito, b: Habito) => a.nome.localeCompare(b.nome);
