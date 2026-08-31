import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Resumo } from './resumo.component';

describe('Resumo', () => {
  let component: Resumo;
  let fixture: ComponentFixture<Resumo>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Resumo],
    }).compileComponents();

    fixture = TestBed.createComponent(Resumo);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
