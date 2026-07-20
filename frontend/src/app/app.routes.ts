import { Routes } from '@angular/router';
import { authGuard, adminGuard, naoAlunoGuard, alunoGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'painel' },
      {
        path: 'painel',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'minha-frequencia',
        canActivate: [alunoGuard],
        loadComponent: () =>
          import('./pages/minha-frequencia/minha-frequencia.component').then((m) => m.MinhaFrequenciaComponent),
      },
      {
        path: 'alunos',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/alunos/alunos.component').then((m) => m.AlunosComponent),
      },
      {
        path: 'chamada',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/chamada/chamada.component').then((m) => m.ChamadaComponent),
      },
      {
        path: 'aulas',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/aulas/aulas.component').then((m) => m.AulasComponent),
      },
      {
        path: 'relatorio',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/relatorio/relatorio.component').then((m) => m.RelatorioComponent),
      },
      {
        path: 'provas',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/provas/provas.component').then((m) => m.ProvasComponent),
      },
      {
        path: 'provas/:id/notas',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/notas/notas.component').then((m) => m.NotasComponent),
      },
      {
        path: 'desafios',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/desafios/desafios.component').then((m) => m.DesafiosComponent),
      },
      {
        path: 'classes',
        loadComponent: () => import('./pages/classes/classes.component').then((m) => m.ClassesComponent),
        canActivate: [adminGuard],
      },
      {
        path: 'usuarios',
        loadComponent: () => import('./pages/usuarios/usuarios.component').then((m) => m.UsuariosComponent),
        canActivate: [adminGuard],
      },
      {
        path: 'campanhas',
        loadComponent: () => import('./pages/campanhas/campanhas.component').then((m) => m.CampanhasComponent),
        canActivate: [adminGuard],
      },
      {
        path: 'relatorio-geral',
        loadComponent: () => import('./pages/relatorio-geral/relatorio-geral.component').then((m) => m.RelatorioGeralComponent),
        canActivate: [adminGuard],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
