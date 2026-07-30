import { expect, type Page } from '@playwright/test';

/**
 * Faz login pela tela real (`/login`) e espera sair dela.
 * Retorna a rota de destino (ex.: `/painel`, `/conta`, `/minha-frequencia`).
 */
export async function entrar(page: Page, usuario: string, senha: string): Promise<string> {
  await page.goto('/login');
  await page.locator('#user').fill(usuario);
  await page.locator('#senha').fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
  // O guard de auth só libera as rotas internas após o token chegar.
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15_000 });
  return new URL(page.url()).pathname;
}
