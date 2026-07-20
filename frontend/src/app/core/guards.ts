import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Exige usuário autenticado. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.logado()) {
    return true;
  }
  router.navigate(['/login']);
  return false;
};

/** Exige perfil ADMIN. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAdmin()) {
    return true;
  }
  router.navigate(['/']);
  return false;
};

/** Bloqueia ALUNO das telas de gestão (chamada, aulas, alunos, relatório, desafios, provas). */
export const naoAlunoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAluno()) {
    return true;
  }
  router.navigate(['/minha-frequencia']);
  return false;
};

/** Exige perfil ALUNO (tela "Minha frequência"). */
export const alunoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAluno()) {
    return true;
  }
  router.navigate(['/']);
  return false;
};
