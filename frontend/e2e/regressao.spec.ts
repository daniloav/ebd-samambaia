import { test, expect } from '@playwright/test';
import { CONTAS } from './util/contas';
import { entrar } from './util/login';

/**
 * Testes de regressão dos fluxos essenciais (autenticação, guards por papel e
 * a renderização das telas). Não dependem de dados criados no teste — usam só
 * o que o `DataInitializer` semeia — para não ficarem frágeis.
 */

test.describe('Autenticação', () => {
  test('recusa credenciais inválidas', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#user').fill('admin');
    await page.locator('#senha').fill('senha-errada');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByText('Usuário ou senha inválidos.')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('admin entra e cai no painel', async ({ page }) => {
    const destino = await entrar(page, CONTAS.admin.usuario, CONTAS.admin.senha);
    expect(destino).toBe('/painel');
  });

  test('professor entra e cai no painel', async ({ page }) => {
    const destino = await entrar(page, CONTAS.professor.usuario, CONTAS.professor.senha);
    expect(destino).toBe('/painel');
  });

  test('aluno no 1º acesso é forçado a trocar a senha (/conta)', async ({ page }) => {
    const destino = await entrar(page, CONTAS.aluno.usuario, CONTAS.aluno.senha);
    expect(destino).toBe('/conta');
  });

  test('rota interna sem sessão redireciona para /login', async ({ page }) => {
    await page.goto('/alunos');
    await expect(page).toHaveURL(/\/login$/);
  });
});

test.describe('Guards por papel', () => {
  test('aluno não acessa área de gestão (/painel)', async ({ page }) => {
    await entrar(page, CONTAS.aluno.usuario, CONTAS.aluno.senha);
    await page.goto('/painel');
    // O guard de aluno tira dele o painel de gestão (não permanece em /painel).
    await expect(page).not.toHaveURL(/\/painel$/);
  });

  test('professor não acessa telas exclusivas de admin (/usuarios)', async ({ page }) => {
    await entrar(page, CONTAS.professor.usuario, CONTAS.professor.senha);
    await page.goto('/usuarios');
    await expect(page).not.toHaveURL(/\/usuarios$/);
  });
});

/**
 * Fumaça de navegação: cada tela de gestão deve renderizar sem erro de console
 * fatal e manter a URL (não ser expulsa por guard). Cobre regressão de rota,
 * lazy-loading do componente e chamada inicial à API.
 */
const TELAS_GESTAO = [
  '/painel',
  '/alunos',
  '/chamada',
  '/aulas',
  '/relatorio',
  '/relatorio-visitantes',
  '/desafios',
  '/provas',
  '/boletim',
  '/classes',
  '/usuarios',
  '/campanhas',
  '/relatorio-geral',
  '/auditoria',
  '/conta',
];

test.describe('Navegação (admin)', () => {
  for (const rota of TELAS_GESTAO) {
    test(`renderiza ${rota}`, async ({ page }) => {
      const erros: string[] = [];
      page.on('pageerror', (e) => erros.push(String(e)));

      await entrar(page, CONTAS.admin.usuario, CONTAS.admin.senha);
      await page.goto(rota);
      await expect(page).toHaveURL(new RegExp(`${rota}$`));
      // A casca (menu lateral) precisa estar presente na tela.
      await expect(page.locator('aside, nav').first()).toBeVisible();
      expect(erros, `Erros de runtime em ${rota}:\n${erros.join('\n')}`).toEqual([]);
    });
  }
});
