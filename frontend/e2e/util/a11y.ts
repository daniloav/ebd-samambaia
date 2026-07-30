import AxeBuilder from '@axe-core/playwright';
import { expect, type Page, type TestInfo } from '@playwright/test';

/** Regras-alvo: WCAG 2.0/2.1, níveis A e AA. */
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

/** Severidades que BLOQUEIAM o build (as demais viram só relatório). */
const BLOQUEANTES = new Set(['serious', 'critical']);

/**
 * Roda o axe-core na página atual, anexa o relatório completo ao teste
 * e falha se houver alguma violação `serious`/`critical`.
 *
 * @param rotulo  nome amigável da tela (usado no anexo e na mensagem de erro).
 */
export async function verificarAcessibilidade(page: Page, testInfo: TestInfo, rotulo: string): Promise<void> {
  const resultado = await new AxeBuilder({ page }).withTags(TAGS).analyze();

  // Relatório completo como artifact (todas as severidades), sempre.
  await testInfo.attach(`axe-${rotulo}.json`, {
    body: JSON.stringify(resultado.violations, null, 2),
    contentType: 'application/json',
  });

  const bloqueantes = resultado.violations.filter((v) => BLOQUEANTES.has(v.impact ?? ''));
  const resumo = bloqueantes
    .map((v) => `  • [${v.impact}] ${v.id}: ${v.help} (${v.nodes.length} ocorrência(s)) — ${v.helpUrl}`)
    .join('\n');

  expect(
    bloqueantes,
    `Acessibilidade (${rotulo}) — ${bloqueantes.length} violação(ões) séria(s)/crítica(s):\n${resumo}`,
  ).toEqual([]);
}
