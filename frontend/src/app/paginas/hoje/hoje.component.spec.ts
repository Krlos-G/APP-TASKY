import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Hoje } from './hoje.component';

describe('Hoje', () => {
  let component: Hoje;
  let fixture: ComponentFixture<Hoje>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Hoje],
    }).compileComponents();

    fixture = TestBed.createComponent(Hoje);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
