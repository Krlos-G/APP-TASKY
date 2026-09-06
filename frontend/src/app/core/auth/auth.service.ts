import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, ReplaySubject, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CredenciaisLogin,
  DadosRegistro,
  RespostaToken,
  UsuarioAutenticado,
} from './auth.models';

/**
 * Header exigido pelo backend em /refresh e /logout.
 *
 * E a defesa de CSRF desses dois endpoints: eles se autenticam por cookie, e um
 * site malicioso ate consegue disparar o POST, mas nao consegue adicionar
 * cabecalho customizado sem passar por preflight de CORS.
 */
export const HEADER_CLIENTE = new HttpHeaders({ 'X-Tasky-Client': 'web' });

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.urlApi}/auth`;

  /**
   * O access token vive apenas em memoria - nunca em localStorage, que qualquer
   * script da pagina consegue ler. O preco e que todo reload perde o token e
   * precisa chamar restaurarSessao().
   */
  private accessTokenAtual: string | null = null;

  private readonly usuarioAtual = signal<UsuarioAutenticado | null>(null);

  readonly usuario = this.usuarioAtual.asReadonly();
  readonly autenticado = computed(() => this.usuarioAtual() !== null);

  /**
   * Renovacao em andamento, compartilhada por todos os interessados.
   *
   * Este campo e o coracao da etapa. Sem ele, cinco requisicoes tomando 401 ao
   * mesmo tempo disparariam cinco rotacoes de refresh; a partir da segunda, o
   * backend veria um token ja usado e - passada a janela de graca - trataria
   * como replay, revogando a familia e derrubando a sessao do proprio usuario.
   */
  private renovacaoEmAndamento: ReplaySubject<string> | null = null;

  get accessToken(): string | null {
    return this.accessTokenAtual;
  }

  registrar(dados: DadosRegistro): Observable<UsuarioAutenticado> {
    return this.http.post<UsuarioAutenticado>(`${this.api}/registrar`, dados);
  }

  entrar(credenciais: CredenciaisLogin): Observable<RespostaToken> {
    return this.http
      .post<RespostaToken>(`${this.api}/login`, credenciais)
      .pipe(tap((resposta) => this.aplicarToken(resposta)));
  }

  /**
   * Renova o access token, garantindo uma unica chamada simultanea.
   *
   * Quem chegar enquanto uma renovacao esta em curso recebe o mesmo resultado,
   * em vez de disparar outra.
   */
  renovar(): Observable<string> {
    if (this.renovacaoEmAndamento) {
      return this.renovacaoEmAndamento.asObservable();
    }

    const emAndamento = new ReplaySubject<string>(1);
    this.renovacaoEmAndamento = emAndamento;

    this.http
      .post<RespostaToken>(`${this.api}/refresh`, {}, { headers: HEADER_CLIENTE })
      .subscribe({
        next: (resposta) => {
          this.aplicarToken(resposta);
          this.renovacaoEmAndamento = null;
          emAndamento.next(resposta.accessToken);
          emAndamento.complete();
        },
        error: (erro) => {
          this.limparSessao();
          this.renovacaoEmAndamento = null;
          emAndamento.error(erro);
        },
      });

    return emAndamento.asObservable();
  }

  sair(): Observable<void> {
    return this.http
      .post<void>(`${this.api}/logout`, {}, { headers: HEADER_CLIENTE })
      .pipe(tap(() => this.limparSessao()));
  }

  /**
   * Tenta reconstruir a sessao a partir do cookie de refresh.
   *
   * Chamado na inicializacao: como o access token so vive em memoria, todo
   * reload comeca sem ele. Falhar aqui e normal - significa apenas que nao ha
   * sessao - e por isso nunca propaga erro.
   */
  restaurarSessao(): Promise<void> {
    return new Promise((resolve) => {
      this.renovar().subscribe({
        next: () => resolve(),
        error: () => resolve(),
      });
    });
  }

  limparSessao(): void {
    this.accessTokenAtual = null;
    this.usuarioAtual.set(null);
  }

  private aplicarToken(resposta: RespostaToken): void {
    this.accessTokenAtual = resposta.accessToken;
    this.usuarioAtual.set(this.lerUsuarioDoToken(resposta.accessToken));
  }

  /**
   * Le o usuario do proprio token, sem ida ao servidor.
   *
   * Nao ha verificacao de assinatura aqui, e nem deveria haver: o cliente usa
   * isto so para exibir nome e e-mail. Quem valida o token de verdade e o
   * backend, a cada requisicao.
   */
  private lerUsuarioDoToken(token: string): UsuarioAutenticado | null {
    try {
      const payload = token.split('.')[1];
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      const dados = JSON.parse(json) as { sub: string; email: string; nome: string };

      return { id: Number(dados.sub), email: dados.email, nome: dados.nome };
    } catch {
      return null;
    }
  }
}
