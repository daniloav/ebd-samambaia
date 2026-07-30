import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { RelatorioPresencaResponse } from '../../core/models';
import { exportarExcel, exportarPdf } from '../../core/export.util';

@Component({
  selector: 'app-relatorio',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .filtros { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .filtros .form-group { margin: 0; }
    .num { text-align: center; }
    .pct { font-weight: 700; }
    .pct.bom { color: var(--verde); }
    .pct.medio { color: #b7791f; }
    .pct.ruim { color: var(--vermelho); }
    .cabecalho-rel { display: flex; gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .cabecalho-rel .box { background: #f7fafc; border-radius: 8px; padding: .6rem 1rem; }
    .cabecalho-rel b { color: var(--azul); }
  `],
  template: `
    <h2>Relatório de presenças</h2>
    <p class="muted">Frequência e itens por aluno no período selecionado.</p>

    <div class="card">
      <div class="filtros">
        <div class="form-group"><label>Trimestre</label>
          <select aria-label="Trimestre" [(ngModel)]="trimestre" (ngModelChange)="aplicarTrimestre()">
            <option [ngValue]="null">Personalizado</option>
            <option [ngValue]="1">1º (Jan-Mar)</option>
            <option [ngValue]="2">2º (Abr-Jun)</option>
            <option [ngValue]="3">3º (Jul-Set)</option>
            <option [ngValue]="4">4º (Out-Dez)</option>
          </select>
        </div>
        @if (trimestre !== null) {
          <div class="form-group"><label>Ano</label>
            <input aria-label="Ano" type="number" min="2000" max="2100" [(ngModel)]="ano" (ngModelChange)="aplicarTrimestre()" style="width:90px" />
          </div>
        }
        <div class="form-group"><label>Início</label><input aria-label="Data de início" type="date" [(ngModel)]="inicio" (ngModelChange)="trimestre = null" /></div>
        <div class="form-group"><label>Fim</label><input aria-label="Data de fim" type="date" [(ngModel)]="fim" (ngModelChange)="trimestre = null" /></div>
        <button class="btn" (click)="gerar()">Gerar relatório</button>
        <button class="btn btn-outline" (click)="limpar()">Limpar filtro</button>
        @if (dados()?.itens?.length) {
          <button class="btn btn-outline" (click)="exportarPdf()">📄 PDF</button>
          <button class="btn btn-outline" (click)="exportarExcel()">📊 Excel</button>
        }
      </div>

      @if (dados(); as d) {
        <div class="cabecalho-rel">
          <div class="box">Período: <b>{{ d.inicio | date:'dd/MM/yyyy' }}</b> a <b>{{ d.fim | date:'dd/MM/yyyy' }}</b></div>
          <div class="box">Total de aulas: <b>{{ d.totalAulas }}</b></div>
          <div class="box">Alunos: <b>{{ d.itens.length }}</b></div>
        </div>
        <div class="tabela-scroll">
          <table class="tabela">
            <thead>
              <tr>
                <th>Aluno</th>
                <th class="num">Presenças</th>
                <th class="num">Faltas</th>
                <th class="num">% Presença</th>
                <th class="num">Bíblia</th>
                <th class="num">Revista</th>
                <th class="num">Lição</th>
                <th class="num">Visitante</th>
              </tr>
            </thead>
            <tbody>
              @for (i of d.itens; track i.alunoId) {
                <tr>
                  <td>{{ i.nome }}</td>
                  <td class="num">{{ i.presencas }}</td>
                  <td class="num">{{ i.faltas }}</td>
                  <td class="num pct" [class.bom]="i.percentualPresenca >= 75"
                      [class.medio]="i.percentualPresenca >= 50 && i.percentualPresenca < 75"
                      [class.ruim]="i.percentualPresenca < 50">
                    {{ i.percentualPresenca }}%
                  </td>
                  <td class="num">{{ i.trouxeBiblia }}</td>
                  <td class="num">{{ i.trouxeRevista }}</td>
                  <td class="num">{{ i.estudouLicao }}</td>
                  <td class="num">{{ i.trouxeVisitante }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (carregando()) {
        <div class="spinner-wrap muted">Gerando...</div>
      }
    </div>
  `,
})
export class RelatorioComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private classeCtx = inject(ClasseContextService);

  inicio = '';
  fim = '';
  trimestre: number | null = null;
  ano = new Date().getFullYear();
  dados = signal<RelatorioPresencaResponse | null>(null);
  carregando = signal(false);

  constructor() {
    effect(() => { this.classeCtx.selecionadaId(); this.gerar(); }, { allowSignalWrites: true });
  }

  gerar(): void {
    this.carregando.set(true);
    this.api.relatorioPresencas(this.inicio || undefined, this.fim || undefined, this.classeCtx.selecionadaId()).subscribe({
      next: (r) => { this.dados.set(r); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao gerar relatório.'); this.carregando.set(false); },
    });
  }

  limpar(): void {
    this.inicio = ''; this.fim = ''; this.trimestre = null;
    this.gerar();
  }

  /** Atalho de trimestre: preenche início/fim e gera. 1=Jan-Mar ... 4=Out-Dez. */
  aplicarTrimestre(): void {
    if (this.trimestre == null) { return; }
    const mesIni = (this.trimestre - 1) * 3;
    this.inicio = this.iso(new Date(this.ano, mesIni, 1));
    this.fim = this.iso(new Date(this.ano, mesIni + 3, 0)); // último dia do 3º mês
    this.gerar();
  }

  private iso(d: Date): string {
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${m}-${dia}`;
  }

  private cols(): string[] {
    return ['Aluno', 'Presenças', 'Faltas', '% Presença', 'Bíblia', 'Revista', 'Lição', 'Visitante'];
  }

  private linhas(d: RelatorioPresencaResponse): (string | number)[][] {
    return d.itens.map((i) =>
      [i.nome, i.presencas, i.faltas, `${i.percentualPresenca}%`, i.trouxeBiblia, i.trouxeRevista, i.estudouLicao, i.trouxeVisitante]);
  }

  private turma(): string {
    const id = this.classeCtx.selecionadaId();
    return this.classeCtx.classes().find((c) => c.id === id)?.nome ?? 'Turma';
  }

  private br(iso: string): string {
    if (!iso) { return '—'; }
    const [y, m, dd] = iso.split('-');
    return `${dd}/${m}/${y}`;
  }

  async exportarPdf(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarPdf('relatorio-presencas', `Relatório de presenças — ${this.turma()}`,
      `Período: ${this.br(d.inicio)} a ${this.br(d.fim)} · Total de aulas: ${d.totalAulas}`, this.cols(), this.linhas(d));
  }

  async exportarExcel(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarExcel('relatorio-presencas', 'Presenças', this.cols(), this.linhas(d));
  }
}
