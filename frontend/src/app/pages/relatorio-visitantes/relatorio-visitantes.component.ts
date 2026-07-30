import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { ToastService } from '../../core/toast.service';
import { RelatorioVisitantesResponse } from '../../core/models';
import { exportarExcel, exportarPdf } from '../../core/export.util';

/** Relatório de visitantes por período — geral (ADMIN) ou por turma. */
@Component({
  selector: 'app-relatorio-visitantes',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .filtros { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .filtros .form-group { margin: 0; }
  `],
  template: `
    <h2>Relatório de visitantes</h2>
    <p class="muted">Visitantes cadastrados no período — geral ou por turma.</p>

    <div class="card">
      <div class="filtros">
        <div class="form-group"><label>Início</label><input type="date" [(ngModel)]="inicio" /></div>
        <div class="form-group"><label>Fim</label><input type="date" [(ngModel)]="fim" /></div>
        <div class="form-group">
          <label>Turma</label>
          <select aria-label="Turma" [(ngModel)]="classeId">
            @if (auth.isAdmin()) { <option [ngValue]="null">Todas as turmas</option> }
            @for (c of classeCtx.classes(); track c.id) {
              <option [ngValue]="c.id">{{ c.nome }}</option>
            }
          </select>
        </div>
        <button class="btn" (click)="gerar()" [disabled]="carregando()">
          {{ carregando() ? 'Carregando...' : 'Gerar relatório' }}
        </button>
        @if (dados()?.itens?.length) {
          <button class="btn btn-outline" (click)="exportarPdf()">📄 PDF</button>
          <button class="btn btn-outline" (click)="exportarExcel()">📊 Excel</button>
        }
      </div>

      @if (dados(); as d) {
        <div style="margin-bottom:1rem">
          <span class="muted">Período <b>{{ d.inicio | date:'dd/MM/yyyy' }}</b> a <b>{{ d.fim | date:'dd/MM/yyyy' }}</b>
            · {{ d.classeNome || 'Todas as turmas' }} · <b>{{ d.total }}</b> visitante(s)</span>
        </div>
        @if (d.itens.length === 0) {
          <p class="muted text-center">Nenhum visitante no período.</p>
        } @else {
          <div class="tabela-scroll">
            <table class="tabela">
              <thead>
                <tr><th>Nome</th><th>Contato</th><th>Turma</th><th>Data</th><th>Trazido por</th></tr>
              </thead>
              <tbody>
                @for (v of d.itens; track v.id) {
                  <tr>
                    <td>{{ v.nome }}</td>
                    <td>{{ contato(v) }}</td>
                    <td>{{ v.turma }}</td>
                    <td>{{ v.dataAula | date:'dd/MM/yyyy' }}</td>
                    <td>{{ v.trazidoPorNome || '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </div>
  `,
})
export class RelatorioVisitantesComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  classeCtx = inject(ClasseContextService);

  inicio = `${new Date().getFullYear()}-01-01`;
  fim = new Date().toISOString().slice(0, 10);
  classeId: number | null = null;
  dados = signal<RelatorioVisitantesResponse | null>(null);
  carregando = signal(false);

  constructor() {
    this.classeCtx.carregar();
    // Professor não vê "Todas": começa na turma atual (ou na primeira).
    if (!this.auth.isAdmin()) {
      this.classeId = this.classeCtx.selecionadaId();
    }
  }

  contato(v: { email: string | null; telefone: string | null }): string {
    return [v.email, v.telefone].filter((x) => !!x).join(' · ') || '—';
  }

  gerar(): void {
    this.carregando.set(true);
    this.api.relatorioVisitantes(this.inicio, this.fim, this.classeId).subscribe({
      next: (d) => { this.dados.set(d); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao gerar o relatório.'); this.carregando.set(false); },
    });
  }

  private cols(): string[] {
    return ['Nome', 'E-mail', 'Telefone', 'Turma', 'Data', 'Trazido por'];
  }

  private linhas(d: RelatorioVisitantesResponse): (string | number)[][] {
    return d.itens.map((v) => [
      v.nome, v.email || '', v.telefone || '', v.turma, this.br(v.dataAula), v.trazidoPorNome || '',
    ]);
  }

  async exportarPdf(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarPdf(`visitantes-${d.inicio}-${d.fim}`, 'Relatório de visitantes — EBD ICES',
      `${d.classeNome || 'Todas as turmas'} · ${this.br(d.inicio)} a ${this.br(d.fim)} · ${d.total} visitante(s)`,
      this.cols(), this.linhas(d));
  }

  async exportarExcel(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarExcel(`visitantes-${d.inicio}-${d.fim}`, 'Visitantes', this.cols(), this.linhas(d));
  }

  private br(iso: string): string {
    if (!iso) { return '—'; }
    const [y, m, dd] = iso.slice(0, 10).split('-');
    return `${dd}/${m}/${y}`;
  }
}
