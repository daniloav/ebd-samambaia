import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { RelatorioPresencaResponse } from '../../core/models';

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
        <div class="form-group"><label>Início</label><input type="date" [(ngModel)]="inicio" /></div>
        <div class="form-group"><label>Fim</label><input type="date" [(ngModel)]="fim" /></div>
        <button class="btn" (click)="gerar()">Gerar relatório</button>
        <button class="btn btn-outline" (click)="limpar()">Limpar filtro</button>
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

  inicio = '';
  fim = '';
  dados = signal<RelatorioPresencaResponse | null>(null);
  carregando = signal(false);

  constructor() {
    this.gerar();
  }

  gerar(): void {
    this.carregando.set(true);
    this.api.relatorioPresencas(this.inicio || undefined, this.fim || undefined).subscribe({
      next: (r) => { this.dados.set(r); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao gerar relatório.'); this.carregando.set(false); },
    });
  }

  limpar(): void {
    this.inicio = ''; this.fim = '';
    this.gerar();
  }
}
