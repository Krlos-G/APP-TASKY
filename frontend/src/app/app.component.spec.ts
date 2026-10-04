import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SwUpdate, VersionEvent } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { App } from './app.component';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { AtualizacaoDoApp } from './core/pwa/atualizacao';

describe('App', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let versoes: Subject<VersionEvent>;

  beforeEach(async () => {
    versoes = new Subject();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SwUpdate,
          useValue: {
            isEnabled: true,
            versionUpdates: versoes,
            unrecoverable: new Subject(),
            checkForUpdate: () => Promise.resolve(false),
          },
        },
      ],
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

  it('manda o fuso do aparelho uma vez, quando ha sessao', () => {
    autenticar();

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/usuarios/eu/fuso');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.fusoHorario).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
    req.flush(null);

    // Uma vez por sessao: redesenhar a tela nao reenvia.
    fixture.detectChanges();
    http.expectNone('/api/v1/usuarios/eu/fuso');
  });

  it('sem sessao, nao manda fuso nenhum', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    http.expectNone('/api/v1/usuarios/eu/fuso');
  });

  it('falha ao sincronizar o fuso nao aparece na tela', () => {
    autenticar();

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    http.expectOne('/api/v1/usuarios/eu/fuso').flush(
      { erro: 'Fuso horario desconhecido: Marte/Olympus' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    // A navegacao segue de pe e nenhum aviso de erro foi parar no shell.
    expect(rotulos(fixture.nativeElement).length).toBe(4);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Marte');
  });

  it('avisa quando ha versao nova, e atualizar recarrega o app', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('.versao')).toBeNull();

    versoes.next({
      type: 'VERSION_READY',
      currentVersion: { hash: 'a' },
      latestVersion: { hash: 'b' },
    });
    fixture.detectChanges();

    expect(elemento.querySelector('.versao')?.textContent).toContain('Nova versão disponível');
    const recarregar = vi
      .spyOn(TestBed.inject(AtualizacaoDoApp), 'recarregar')
      .mockImplementation(() => undefined);
    elemento.querySelector<HTMLButtonElement>('.versao button')!.click();
    expect(recarregar).toHaveBeenCalled();
  });
});
