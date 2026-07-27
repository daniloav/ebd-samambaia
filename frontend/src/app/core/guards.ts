import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, RouterStateSnapshot, Router } from '@angular/router';
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
  if (auth.isProfessor() || auth.isAdmin()) {
    return true;
  }
  router.navigate([auth.isAluno() ? '/minha-frequencia' : '/requisicoes']);
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

/**
 * 1º acesso: enquanto o usuário precisar trocar a senha, prende-o na tela de conta.
 * Liberado apenas o caminho /conta (onde a troca acontece).
 */
export const senhaGuard: CanActivateChildFn = (_route, state: RouterStateSnapshot) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.precisaTrocarSenha() && !state.url.startsWith('/conta')) {
    router.navigate(['/conta']);
    return false;
  }
  return true;
};

/** Acesso ao módulo de tesouraria: ADMIN, TESOUREIRO ou LIDER. */
export const tesourariaGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAdmin() || auth.isTesoureiro() || auth.isLider()) {
    return true;
  }
  router.navigate(['/']);
  return false;
};
