import { Routes } from '@angular/router';
import { autenticacaoGuard, visitanteGuard } from './core/auth/auth.guard';
import { casarAba } from './core/ui/abas';

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
    // As quatro abas numa rota só: trocar de aba reaproveita a trilha. O título
    // de cada uma quem põe é a própria trilha.
    matcher: casarAba,
    canActivate: [autenticacaoGuard],
    loadComponent: () => import('./paginas/trilha/trilha.component').then((m) => m.Trilha),
  },
  {
    path: 'rotina',
    canActivate: [autenticacaoGuard],
    title: 'Rotina · Tasky',
    loadComponent: () => import('./paginas/rotina/rotina.component').then((m) => m.Rotina),
  },
  {
    path: 'ajustes',
    canActivate: [autenticacaoGuard],
    title: 'Ajustes · Tasky',
    loadComponent: () => import('./paginas/ajustes/ajustes.component').then((m) => m.Ajustes),
  },
  {
    path: 'tarefas/nova',
    canActivate: [autenticacaoGuard],
    title: 'Nova tarefa · Tasky',
    loadComponent: () =>
      import('./paginas/tarefa-edicao/tarefa-edicao.component').then((m) => m.TarefaEdicao),
  },
  {
    path: 'tarefas/:id',
    canActivate: [autenticacaoGuard],
    title: 'Tarefa · Tasky',
    loadComponent: () =>
      import('./paginas/tarefa-edicao/tarefa-edicao.component').then((m) => m.TarefaEdicao),
  },
  { path: '**', redirectTo: 'hoje' },
];
