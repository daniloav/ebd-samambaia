/**
 * Exportação de relatórios para Excel (.xlsx) e PDF.
 * As bibliotecas (xlsx, jspdf, jspdf-autotable) são carregadas sob demanda
 * (import dinâmico) para não pesar o bundle inicial.
 */

export type Celula = string | number;

/** Gera e baixa um .xlsx com uma planilha (cabeçalho + linhas). */
export async function exportarExcel(
  nomeArquivo: string,
  planilha: string,
  colunas: string[],
  linhas: Celula[][],
): Promise<void> {
  const XLSX: any = await import('xlsx');
  const ws = XLSX.utils.aoa_to_sheet([colunas, ...linhas]);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, planilha.slice(0, 31)); // nome de aba: máx 31 chars
  XLSX.writeFile(wb, `${nomeArquivo}.xlsx`);
}

/** Gera e baixa um PDF com título, subtítulo e uma tabela formatada. */
export async function exportarPdf(
  nomeArquivo: string,
  titulo: string,
  subtitulo: string,
  colunas: string[],
  linhas: Celula[][],
): Promise<void> {
  const jspdfMod: any = await import('jspdf');
  const jsPDF = jspdfMod.jsPDF ?? jspdfMod.default;
  const autoTableMod: any = await import('jspdf-autotable');
  const autoTable = autoTableMod.autoTable ?? autoTableMod.default;

  const doc = new jsPDF({ orientation: colunas.length > 6 ? 'landscape' : 'portrait' });
  doc.setFontSize(14);
  doc.text(titulo, 14, 15);
  if (subtitulo) {
    doc.setFontSize(10);
    doc.setTextColor(110);
    doc.text(subtitulo, 14, 21);
  }
  autoTable(doc, {
    head: [colunas],
    body: linhas.map((r) => r.map((c) => String(c))),
    startY: subtitulo ? 26 : 20,
    styles: { fontSize: 8, cellPadding: 2 },
    headStyles: { fillColor: [27, 58, 91] }, // azul da marca
    alternateRowStyles: { fillColor: [247, 247, 245] },
  });
  doc.save(`${nomeArquivo}.pdf`);
}
