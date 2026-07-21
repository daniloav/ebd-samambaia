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

/** Formata uma data ISO (yyyy-mm-dd) como dd/mm/yyyy. */
function brData(iso: string): string {
  if (!iso) { return '—'; }
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

/**
 * Gera e baixa o boletim de um aluno como PDF (cabeçalho + dados + provas +
 * frequência + situação). Recebe o objeto BoletimResponse da API.
 */
export async function exportarBoletimPdf(b: any): Promise<void> {
  const jspdfMod: any = await import('jspdf');
  const jsPDF = jspdfMod.jsPDF ?? jspdfMod.default;
  const autoTableMod: any = await import('jspdf-autotable');
  const autoTable = autoTableMod.autoTable ?? autoTableMod.default;

  const doc = new jsPDF({ orientation: 'portrait' });
  const azul: [number, number, number] = [27, 58, 91];

  // Cabeçalho
  doc.setFillColor(...azul);
  doc.rect(0, 0, 210, 26, 'F');
  doc.setTextColor(255);
  doc.setFontSize(16);
  doc.text('Boletim — Escola Bíblica Dominical', 14, 12);
  doc.setFontSize(10);
  doc.setTextColor(201, 162, 75);
  doc.text('ICE Samambaia', 14, 19);

  // Dados do aluno
  doc.setTextColor(40);
  doc.setFontSize(11);
  doc.text(`Aluno: ${b.alunoNome}`, 14, 36);
  doc.text(`Turma: ${b.turma}`, 14, 43);
  doc.text(`${b.trimestre}º trimestre de ${b.ano}  (${brData(b.periodoInicio)} a ${brData(b.periodoFim)})`, 14, 50);

  // Provas
  autoTable(doc, {
    startY: 56,
    head: [['Prova', 'Data', 'Nota', 'Máx.', 'Aproveitamento']],
    body: (b.provas ?? []).map((p: any) => [
      p.titulo,
      brData(p.data),
      p.nota != null ? String(p.nota) : '—',
      String(p.notaMaxima),
      p.percentual != null ? `${p.percentual}%` : '—',
    ]),
    styles: { fontSize: 9, cellPadding: 2 },
    headStyles: { fillColor: azul },
    alternateRowStyles: { fillColor: [247, 247, 245] },
  });
  let y = (doc as any).lastAutoTable.finalY + 6;
  doc.setFontSize(10);
  doc.text(`Média das notas: ${b.mediaNotas}    ·    Aproveitamento: ${b.aproveitamentoPct}%`, 14, y);

  // Frequência
  const f = b.frequencia;
  autoTable(doc, {
    startY: y + 5,
    head: [['Aulas', 'Presenças', 'Faltas', '% Presença', 'Bíblia', 'Revista', 'Lição']],
    body: [[f.totalAulas, f.presencas, f.faltas, `${f.percentualPresenca}%`, f.biblias, f.revistas, f.licoes]],
    styles: { fontSize: 9, cellPadding: 2, halign: 'center' },
    headStyles: { fillColor: azul },
  });
  y = (doc as any).lastAutoTable.finalY + 8;

  // Situação
  doc.setFontSize(12);
  doc.setTextColor(...azul);
  doc.text(`Situação: ${b.situacao}`, 14, y);
  if (b.visitantesTrazidos) {
    doc.setFontSize(9);
    doc.setTextColor(110);
    doc.text(`Visitantes trazidos no período: ${b.visitantesTrazidos}`, 14, y + 6);
  }

  doc.save(`boletim-${b.alunoNome.replace(/\s+/g, '-').toLowerCase()}-${b.ano}-T${b.trimestre}.pdf`);
}
