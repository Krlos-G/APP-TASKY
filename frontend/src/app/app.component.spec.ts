import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app.component';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)],
    }).compileComponents();
  });

  it('cria o shell da aplicacao', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('mostra as quatro abas principais', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const rotulos = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.nav__rotulo'),
    ).map((el) => el.textContent?.trim());

    expect(rotulos).toEqual(['Hoje', 'Resumo', 'Hábitos', 'Tarefas']);
  });
});
