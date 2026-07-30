import { test } from '@playwright/test';
import { CONTAS } from './util/contas';
import { entrar } from './util/login';
import { verificarAcessibilidade } from './util/a11y';

/**
 * Auditoria de acessibilidade (WCAG 2.1 A/AA) com axe-core.
 * Falha em violação `serious`/`critical`; o restante vira relatório (anexo).
 */

test.describe('Acessibilidade · telas públicas', () => {
  test('login', async ({ page }, testInfo) => {
    await page.goto('/login');
    await verificarAcessibilidade(page, testInfo, 'login');
  });

  test('recuperar senha', async ({ page }, testInfo) => {
    await page.goto('/recuperar');
    await verificarAcessibilidade(page, testInfo, 'recuperar');
  });
});

// Telas de gestão auditadas após login de admin.
const TELAS_GESTAO = [
  '/painel',
  '/alunos',
  '/chamada',
  '/aulas',
  '/relatorio',
  '/desafios',
  '/provas',
  '/boletim',
  '/usuarios',
  '/conta',
];

test.describe('Acessibilidade · área de gestão (admin)', () => {
  test.beforeEach(async ({ page }) => {
    await entrar(page, CONTAS.admin.usuario, CONTAS.admin.senha);
  });

  for (const rota of TELAS_GESTAO) {
    test(`tela ${rota}`, async ({ page }, testInfo) => {
      await page.goto(rota);
      // Dá tempo do componente lazy montar e a chamada inicial pintar a tela.
      await page.waitForLoadState('networkidle');
      await verificarAcessibilidade(page, testInfo, rota.replace(/\//g, '') || 'raiz');
    });
  }
});

test.describe('Acessibilidade · área do aluno', () => {
  test('troca de senha obrigatória (/conta)', async ({ page }, testInfo) => {
    await entrar(page, CONTAS.aluno.usuario, CONTAS.aluno.senha);
    await page.waitForLoadState('networkidle');
    await verificarAcessibilidade(page, testInfo, 'aluno-conta');
  });
});
