import { Routes } from '@angular/router';

/**
 * As quatro abas principais. Rotina e Ajustes ficam acessiveis a partir do
 * Resumo, por serem editadas com pouca frequencia depois do setup inicial.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'hoje' },
  {
    path: 'hoje',
    title: 'Hoje · Tasky',
    loadComponent: () => import('./paginas/hoje/hoje.component').then((m) => m.Hoje),
  },
  {
    path: 'resumo',
    title: 'Resumo · Tasky',
    loadComponent: () => import('./paginas/resumo/resumo.component').then((m) => m.Resumo),
  },
  {
    path: 'habitos',
    title: 'Hábitos · Tasky',
    loadComponent: () => import('./paginas/habitos/habitos.component').then((m) => m.Habitos),
  },
  {
    path: 'tarefas',
    title: 'Tarefas · Tasky',
    loadComponent: () => import('./paginas/tarefas/tarefas.component').then((m) => m.Tarefas),
  },
  { path: '**', redirectTo: 'hoje' },
];
