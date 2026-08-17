import { Routes } from '@angular/router';
import { authGuard, adminGuard, naoAlunoGuard, alunoGuard, senhaGuard, tesourariaGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'recuperar',
    loadComponent: () => import('./pages/recuperar-senha/recuperar-senha.component').then((m) => m.RecuperarSenhaComponent),
  },
  {
    path: 'redefinir',
    loadComponent: () => import('./pages/recuperar-senha/redefinir-senha.component').then((m) => m.RedefinirSenhaComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    canActivateChild: [senhaGuard],
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
        path: 'meu-boletim',
        canActivate: [alunoGuard],
        loadComponent: () => import('./pages/meu-boletim/meu-boletim.component').then((m) => m.MeuBoletimComponent),
      },
      {
        path: 'meu-ranking',
        canActivate: [alunoGuard],
        loadComponent: () => import('./pages/meu-ranking/meu-ranking.component').then((m) => m.MeuRankingComponent),
      },
      {
        path: 'requisicoes',
        canActivate: [tesourariaGuard],
        loadComponent: () => import('./pages/requisicoes/requisicoes.component').then((m) => m.RequisicoesComponent),
      },
      {
        path: 'minhas-provas',
        canActivate: [alunoGuard],
        loadComponent: () => import('./pages/minhas-provas/minhas-provas.component').then((m) => m.MinhasProvasComponent),
      },
      {
        path: 'minhas-provas/:id',
        canActivate: [alunoGuard],
        loadComponent: () => import('./pages/minhas-provas/responder-prova.component').then((m) => m.ResponderProvaComponent),
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
        path: 'provas/:id/questoes',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/quiz-editor/quiz-editor.component').then((m) => m.QuizEditorComponent),
      },
      {
        path: 'desafios',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/desafios/desafios.component').then((m) => m.DesafiosComponent),
      },
      {
        path: 'relatorio-mensal',
        canActivate: [naoAlunoGuard],
        loadComponent: () =>
          import('./pages/relatorio-mensal/relatorio-mensal.component').then((m) => m.RelatorioMensalComponent),
      },
      {
        path: 'relatorio-visitantes',
        canActivate: [naoAlunoGuard],
        loadComponent: () =>
          import('./pages/relatorio-visitantes/relatorio-visitantes.component').then((m) => m.RelatorioVisitantesComponent),
      },
      {
        path: 'relatorio-inativados',
        canActivate: [naoAlunoGuard],
        loadComponent: () =>
          import('./pages/relatorio-inativados/relatorio-inativados.component').then((m) => m.RelatorioInativadosComponent),
      },
      {
        path: 'boletim',
        canActivate: [naoAlunoGuard],
        loadComponent: () => import('./pages/boletim/boletim.component').then((m) => m.BoletimComponent),
      },
      {
        path: 'conta',
        loadComponent: () => import('./pages/conta/conta.component').then((m) => m.ContaComponent),
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
      {
        path: 'auditoria',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/auditoria/auditoria.component').then((m) => m.AuditoriaComponent),
      },
      {
        path: 'uso',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/uso/uso.component').then((m) => m.UsoComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
