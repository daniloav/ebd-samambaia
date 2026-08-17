import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { ToastService } from '../../core/toast.service';
import { RelatorioInativadosItem, RelatorioInativadosResponse } from '../../core/models';
import { exportarExcel, exportarPdf } from '../../core/export.util';

/** Relatório de alunos inativados — quem saiu, quando, por quê e quem já voltou. */
@Component({
  selector: 'app-relatorio-inativados',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .filtros { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .filtros .form-group { margin: 0; }
    .check { display: flex; align-items: center; gap: .4rem; padding-bottom: .55rem; }
    .resumo { display: flex; gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .resumo .num { font-size: 1.5rem; font-weight: 700; display: block; }
  `],
  template: `
    <h2>Alunos inativados</h2>
    <p class="muted">Quem saiu da chamada, quando e por quê — com a última presença, para a busca.</p>

    <div class="card">
      <div class="filtros">
        <div class="form-group"><label for="ini">Início</label><input id="ini" type="date" [(ngModel)]="inicio" /></div>
        <div class="form-group"><label for="fim">Fim</label><input id="fim" type="date" [(ngModel)]="fim" /></div>
        <div class="form-group">
          <label for="turma">Turma</label>
          <select id="turma" aria-label="Turma" [(ngModel)]="classeId">
            @if (auth.isAdmin()) { <option [ngValue]="null">Todas as turmas</option> }
            @for (c of classeCtx.classes(); track c.id) {
              <option [ngValue]="c.id">{{ c.nome }}</option>
            }
          </select>
        </div>
        <label class="check">
          <input type="checkbox" [(ngModel)]="incluirReativados" /> Incluir quem já voltou
        </label>
        <button class="btn" (click)="gerar()" [disabled]="carregando()">
          {{ carregando() ? 'Carregando...' : 'Gerar relatório' }}
        </button>
        @if (dados()?.itens?.length) {
          <button class="btn btn-outline" (click)="exportarPdf()">📄 PDF</button>
          <button class="btn btn-outline" (click)="exportarExcel()">📊 Excel</button>
        }
      </div>

      @if (dados(); as d) {
        <div class="resumo">
          <div><span class="num">{{ d.total }}</span><span class="muted">inativações</span></div>
          <div><span class="num">{{ d.aindaInativos }}</span><span class="muted">ainda inativos</span></div>
          @if (d.reativados > 0) {
            <div><span class="num">{{ d.reativados }}</span><span class="muted">voltaram</span></div>
          }
          <div><span class="num">{{ d.porFaltasSeguidas }}</span><span class="muted">por faltas seguidas</span></div>
          <div><span class="num">{{ d.manuais }}</span><span class="muted">pelo cadastro</span></div>
        </div>
        <p class="muted" style="margin-bottom:1rem">
          {{ d.periodoAberto ? 'Todo o período' : (d.inicio | date:'dd/MM/yyyy') + ' a ' + (d.fim | date:'dd/MM/yyyy') }}
          · {{ d.classeNome || 'Todas as turmas' }}
        </p>
        @if (!d.periodoAberto && d.semDataRegistrada > 0) {
          <p class="muted">
            ⚠️ {{ d.semDataRegistrada }} inativação(ões) antiga(s) sem data registrada ficaram de fora —
            limpe o período para vê-las.
          </p>
        }

        @if (d.itens.length === 0) {
          <p class="muted text-center">Nenhum aluno inativado no período.</p>
        } @else {
          <div class="tabela-scroll">
            <table class="tabela">
              <thead>
                <tr>
                  <th>Aluno</th><th>Turma</th><th>Contato</th><th>Inativado em</th>
                  <th>Motivo</th><th>Última presença</th><th>Situação</th>
                </tr>
              </thead>
              <tbody>
                @for (i of d.itens; track i.alunoId + '-' + i.inativadoEm) {
                  <tr>
                    <td>{{ i.nome }}</td>
                    <td>{{ i.turma }}</td>
                    <td>{{ contato(i) }}</td>
                    <td>{{ i.inativadoEm ? (i.inativadoEm | date:'dd/MM/yyyy') : 'Antes do registro' }}</td>
                    <td>{{ motivo(i) }}</td>
                    <td>{{ i.ultimaPresenca ? (i.ultimaPresenca | date:'dd/MM/yyyy') : 'Nunca veio' }}</td>
                    <td>
                      @if (i.reativadoEm) {
                        <span class="badge badge-verde">Voltou em {{ i.reativadoEm | date:'dd/MM/yyyy' }}</span>
                      } @else {
                        <span class="badge badge-cinza">Inativo</span>
                      }
                    </td>
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
export class RelatorioInativadosComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  classeCtx = inject(ClasseContextService);

  // Sem período por padrão: mostra tudo, inclusive as inativações antigas sem data.
  inicio = '';
  fim = '';
  classeId: number | null = null;
  incluirReativados = false;
  dados = signal<RelatorioInativadosResponse | null>(null);
  carregando = signal(false);

  constructor() {
    this.classeCtx.carregar();
    if (!this.auth.isAdmin()) {
      this.classeId = this.classeCtx.selecionadaId();
    }
    this.gerar();
  }

  contato(i: RelatorioInativadosItem): string {
    return [i.email, i.telefone].filter((x) => !!x).join(' · ') || '—';
  }

  motivo(i: RelatorioInativadosItem): string {
    switch (i.motivo) {
      case 'FALTAS_SEGUIDAS':
        return `${i.faltasSeguidas ?? 5} faltas seguidas`;
      case 'MANUAL':
        return `Pelo cadastro${i.inativadoPor ? ' (' + i.inativadoPor + ')' : ''}`;
      default:
        return 'Não registrado';
    }
  }

  gerar(): void {
    this.carregando.set(true);
    this.api.relatorioInativados(this.inicio, this.fim, this.classeId, this.incluirReativados).subscribe({
      next: (d) => { this.dados.set(d); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao gerar o relatório.'); this.carregando.set(false); },
    });
  }

  private cols(): string[] {
    return ['Aluno', 'Turma', 'E-mail', 'Telefone', 'Inativado em', 'Motivo', 'Última presença', 'Situação'];
  }

  private linhas(d: RelatorioInativadosResponse): (string | number)[][] {
    return d.itens.map((i) => [
      i.nome, i.turma, i.email || '', i.telefone || '',
      i.inativadoEm ? this.br(i.inativadoEm) : 'Antes do registro',
      this.motivo(i),
      i.ultimaPresenca ? this.br(i.ultimaPresenca) : 'Nunca veio',
      i.reativadoEm ? `Voltou em ${this.br(i.reativadoEm)}` : 'Inativo',
    ]);
  }

  private periodoLabel(d: RelatorioInativadosResponse): string {
    return d.periodoAberto ? 'Todo o período' : `${this.br(d.inicio)} a ${this.br(d.fim)}`;
  }

  async exportarPdf(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarPdf(`alunos-inativados-${d.inicio}-${d.fim}`, 'Alunos inativados — EBD ICES',
      `${d.classeNome || 'Todas as turmas'} · ${this.periodoLabel(d)} · ${d.total} inativação(ões)`,
      this.cols(), this.linhas(d));
  }

  async exportarExcel(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    await exportarExcel(`alunos-inativados-${d.inicio}-${d.fim}`, 'Inativados', this.cols(), this.linhas(d));
  }

  private br(iso: string): string {
    if (!iso) { return '—'; }
    const [y, m, dd] = iso.slice(0, 10).split('-');
    return `${dd}/${m}/${y}`;
  }
}
