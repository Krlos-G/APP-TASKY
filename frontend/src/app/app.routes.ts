import { Routes } from '@angular/router';
import { autenticacaoGuard, visitanteGuard } from './core/auth/auth.guard';

/**
 * As quatro abas principais. Rotina e Ajustes ficam acessiveis a partir do
 * Resumo, por serem editadas com pouca frequencia depois do setup inicial.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'hoje' },
  {
    path: 'login',
    title: 'Entrar · Tasky',
    canActivate: [visitanteGuard],
    loadComponent: () => import('./paginas/login/login.component').then((m) => m.Login),
  },
  {
    path: 'hoje',
    canActivate: [autenticacaoGuard],
    title: 'Hoje · Tasky',
    loadComponent: () => import('./paginas/hoje/hoje.component').then((m) => m.Hoje),
  },
  {
    path: 'rotina',
    canActivate: [autenticacaoGuard],
    title: 'Rotina · Tasky',
    loadComponent: () => import('./paginas/rotina/rotina.component').then((m) => m.Rotina),
  },
  {
    path: 'resumo',
    canActivate: [autenticacaoGuard],
    title: 'Resumo · Tasky',
    loadComponent: () => import('./paginas/resumo/resumo.component').then((m) => m.Resumo),
  },
  {
    path: 'habitos',
    canActivate: [autenticacaoGuard],
    title: 'Hábitos · Tasky',
    loadComponent: () => import('./paginas/habitos/habitos.component').then((m) => m.Habitos),
  },
  {
    path: 'tarefas',
    canActivate: [autenticacaoGuard],
    title: 'Tarefas · Tasky',
    loadComponent: () => import('./paginas/tarefas/tarefas.component').then((m) => m.Tarefas),
  },
  { path: '**', redirectTo: 'hoje' },
];
