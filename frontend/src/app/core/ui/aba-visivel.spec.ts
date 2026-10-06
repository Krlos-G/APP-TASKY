import { TestBed } from '@angular/core/testing';
import { AbaVisivel } from './aba-visivel';

describe('AbaVisivel', () => {
  it('avisa so a aba que voltou a ficar visivel', () => {
    const aba = TestBed.inject(AbaVisivel);
    const avisos: string[] = [];
    aba.voltou('/hoje').subscribe(() => avisos.push('hoje'));
    aba.voltou('/tarefas').subscribe(() => avisos.push('tarefas'));

    aba.avisar('/tarefas');

    expect(avisos).toEqual(['tarefas']);
  });
});
