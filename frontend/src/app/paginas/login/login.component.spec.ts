import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { Login } from './login.component';

function respostaValida() {
  const payload = btoa(JSON.stringify({ sub: '1', email: 'a@b.com', nome: 'Carlos' }));
  return { accessToken: `x.${payload}.y`, expiraEm: '2026-12-31T23:59:59Z' };
}

describe('Login', () => {
  let http: HttpTestingController;
  let router: Router;
  let returnUrl: string | null = null;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}),
            },
          },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  function preencher(fixture: ReturnType<typeof TestBed.createComponent<Login>>,
                     email: string, senha: string): void {
    const elemento = fixture.nativeElement as HTMLElement;
    const campos = elemento.querySelectorAll<HTMLInputElement>('.campo__entrada');
    campos[0].value = email;
    campos[0].dispatchEvent(new Event('input'));
    campos[1].value = senha;
    campos[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('nao envia com o formulario invalido', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone('/api/v1/auth/login');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.campo__erro').length)
      .toBeGreaterThan(0);
  });

  it('entra e navega para a tela inicial', () => {
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();

    preencher(fixture, 'carlos@tasky.app', 'minha-senha-longa');
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit'));

    http.expectOne('/api/v1/auth/login').flush(respostaValida());

    expect(navegar).toHaveBeenCalledWith('/hoje');
  });

  it('mostra a mensagem do backend quando as credenciais falham', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();

    preencher(fixture, 'carlos@tasky.app', 'senha-errada');
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit'));

    http.expectOne('/api/v1/auth/login').flush(
      { erro: 'E-mail ou senha invalidos.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    const alerta = (fixture.nativeElement as HTMLElement).querySelector('.login__falha');
    expect(alerta?.textContent).toContain('E-mail ou senha invalidos.');
  });

  it('explica o bloqueio por excesso de tentativas', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();

    preencher(fixture, 'carlos@tasky.app', 'senha-qualquer');
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit'));

    http.expectOne('/api/v1/auth/login')
      .flush({}, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();

    // Dizer "senha invalida" aqui confundiria quem esta so esperando o limite.
    const alerta = (fixture.nativeElement as HTMLElement).querySelector('.login__falha');
    expect(alerta?.textContent).toContain('Muitas tentativas');
  });
});
