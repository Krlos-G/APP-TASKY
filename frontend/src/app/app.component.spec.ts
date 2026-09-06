import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app.component';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  function autenticar(): void {
    const payload = btoa(JSON.stringify({ sub: '1', email: 'a@b.com', nome: 'Carlos' }));
    auth.entrar({ email: 'a@b.com', senha: 'senha-longa' }).subscribe();
    http.expectOne('/api/v1/auth/login')
      .flush({ accessToken: `x.${payload}.y`, expiraEm: '2026-12-31T23:59:59Z' });
  }

  function rotulos(elemento: HTMLElement): (string | undefined)[] {
    return Array.from(elemento.querySelectorAll('.nav__rotulo')).map((el) =>
      el.textContent?.trim(),
    );
  }

  it('cria o shell da aplicacao', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('esconde a navegacao quando nao ha sessao', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    // Sem isto, a tela de login apareceria com a tab bar por cima.
    expect(rotulos(fixture.nativeElement).length).toBe(0);
  });

  it('mostra as quatro abas quando autenticado', () => {
    autenticar();

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(rotulos(fixture.nativeElement)).toEqual(['Hoje', 'Resumo', 'Hábitos', 'Tarefas']);
  });
});
