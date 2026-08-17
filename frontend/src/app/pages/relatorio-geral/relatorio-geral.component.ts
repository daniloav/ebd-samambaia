import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { RelatorioGeralResponse } from '../../core/models';
import { exportarExcel, exportarPdf } from '../../core/export.util';

@Component({
  selector: 'app-relatorio-geral',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div style="margin-bottom:1.25rem">
      <h2>Relatório geral do dia</h2>
      <p class="muted">Consolidado de todas as turmas — visão da superintendência.</p>
    </div>

    <div class="card" style="margin-bottom:1.5rem">
      <div style="display:flex;gap:.8rem;align-items:flex-end;flex-wrap:wrap">
        <div class="form-group" style="margin:0"><label>Data (domingo)</label>
          <input type="date" [(ngModel)]="data" /></div>
        <button class="btn btn-verde" (click)="buscar()" [disabled]="carregando()">
          {{ carregando() ? 'Carregando...' : 'Ver relatório' }}
        </button>
        @if (dados()?.turmas?.length) {
          <button class="btn btn-outline" (click)="exportarPdf()">📄 PDF</button>
          <button class="btn btn-outline" (click)="exportarExcel()">📊 Excel</button>
        }
      </div>
    </div>

    @if (carregando()) {
      <div class="card"><div class="spinner-wrap muted">Carregando...</div></div>
    } @else {
      @if (dados(); as d) {
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:1rem;margin-bottom:1.5rem">
          <div class="card text-center"><div class="muted">Presentes</div><strong style="font-size:1.6rem;color:#2f855a">{{ d.totais.presentes }}</strong></div>
          <div class="card text-center"><div class="muted">Faltosos</div><strong style="font-size:1.6rem;color:#c53030">{{ d.totais.faltosos }}</strong></div>
          <div class="card text-center"><div class="muted">Bíblias</div><strong style="font-size:1.6rem">{{ d.totais.biblias }}</strong></div>
          <div class="card text-center"><div class="muted">Revistas</div><strong style="font-size:1.6rem">{{ d.totais.revistas }}</strong></div>
          <div class="card text-center"><div class="muted">Lições</div><strong style="font-size:1.6rem">{{ d.totais.licoes }}</strong></div>
          <div class="card text-center"><div class="muted">Visitantes</div><strong style="font-size:1.6rem;color:var(--alerta)">{{ d.totais.visitantes }}</strong></div>
        </div>

        <div class="card">
          <h3 style="margin-top:0">Por turma — {{ d.data | date:'dd/MM/yyyy' }}</h3>
          @if (d.turmas.length === 0) {
            <p class="muted text-center">Nenhuma aula registrada nesta data.</p>
          } @else {
            <div class="tabela-scroll">
              <table class="tabela">
                <thead>
                  <tr>
                    <th>Turma</th><th>Presentes</th><th>Faltosos</th>
                    <th>Bíblias</th><th>Revistas</th><th>Lições</th><th>Visitantes</th>
                  </tr>
                </thead>
                <tbody>
                  @for (t of d.turmas; track t.classeId) {
                    <tr>
                      <td><strong>{{ t.classeNome }}</strong>{{ t.tema ? ' — ' + t.tema : '' }}</td>
                      <td>{{ t.presentes }}</td>
                      <td>{{ t.faltosos }}</td>
                      <td>{{ t.biblias }}</td>
                      <td>{{ t.revistas }}</td>
                      <td>{{ t.licoes }}</td>
                      <td>{{ t.visitantes }}</td>
                    </tr>
                  }
                </tbody>
                <tfoot>
                  <tr style="font-weight:bold;border-top:2px solid #e2e8f0">
                    <td>Total ({{ d.totalTurmas }} turma(s))</td>
                    <td>{{ d.totais.presentes }}</td>
                    <td>{{ d.totais.faltosos }}</td>
                    <td>{{ d.totais.biblias }}</td>
                    <td>{{ d.totais.revistas }}</td>
                    <td>{{ d.totais.licoes }}</td>
                    <td>{{ d.totais.visitantes }}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          }
        </div>
      } @else {
        <div class="card"><p class="muted text-center">Escolha uma data e clique em "Ver relatório".</p></div>
      }
    }
  `,
})
export class RelatorioGeralComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  data = new Date().toISOString().slice(0, 10);
  dados = signal<RelatorioGeralResponse | null>(null);
  carregando = signal(false);

  constructor() {
    this.buscar();
  }

  buscar(): void {
    this.carregando.set(true);
    this.api.relatorioGeral(this.data).subscribe({
      next: (d) => { this.dados.set(d); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao carregar o relatório geral.'); this.carregando.set(false); },
    });
  }

  private cols(): string[] {
    return ['Turma', 'Presentes', 'Faltosos', 'Bíblias', 'Revistas', 'Lições', 'Visitantes'];
  }

  private linhas(d: RelatorioGeralResponse): (string | number)[][] {
    const rows: (string | number)[][] = d.turmas.map((t) =>
      [t.classeNome + (t.tema ? ' — ' + t.tema : ''), t.presentes, t.faltosos, t.biblias, t.revistas, t.licoes, t.visitantes]);
    rows.push(['TOTAL', d.totais.presentes, d.totais.faltosos, d.totais.biblias, d.totais.revistas, d.totais.licoes, d.totais.visitantes]);
    return rows;
  }

  async exportarPdf(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarPdf(`relatorio-geral-${d.data}`, 'Relatório geral do dia — EBD ICES',
      `Data: ${this.br(d.data)} · Turmas: ${d.totalTurmas}`, this.cols(), this.linhas(d));
  }

  async exportarExcel(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarExcel(`relatorio-geral-${d.data}`, 'Relatório geral', this.cols(), this.linhas(d));
  }

  private br(iso: string): string {
    if (!iso) { return '—'; }
    const [y, m, dd] = iso.split('-');
    return `${dd}/${m}/${y}`;
  }
}
