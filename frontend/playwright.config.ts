import { defineConfig, devices } from '@playwright/test';

/**
 * Configuração dos testes de ponta-a-ponta (E2E) e acessibilidade.
 *
 * O Playwright sobe apenas o **frontend** (`ng serve` em :4200). O **backend**
 * (Quarkus em :8080) e o **Postgres** precisam estar no ar por fora:
 *   - Local:  `mvn quarkus:dev` (perfil dev já libera CORS para :4200).
 *   - CI:     a esteira sobe o jar com CORS habilitado antes de rodar os testes.
 *
 * Rode com:  `npm run e2e`  (headless)  ·  `npm run e2e:ui`  (modo interativo).
 */
export default defineConfig({
  testDir: './e2e',
  // Um passo de cada vez: os fluxos compartilham o mesmo banco semeado.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    locale: 'pt-BR',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Sobe o dev server do Angular; reaproveita se já estiver rodando.
  webServer: {
    command: 'npx ng serve --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
