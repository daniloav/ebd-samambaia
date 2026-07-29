import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { BoletimResponse } from '../../core/models';
import { exportarBoletimPdf } from '../../core/export.util';

/** Renderização compartilhada do boletim (usada pela tela do aluno e a do professor/admin). */
@Component({
  selector: 'app-boletim-view',
  standalone: true,
  imports: [DatePipe],
  styles: [`
    .topo { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; }
    .sub { color: #718096; font-size: .9rem; }
    .num { text-align: center; }
    .metricas { display: flex; gap: 1.5rem; flex-wrap: wrap; margin: 1rem 0; }
    .metricas .box { background: #f7fafc; border-radius: 8px; padding: .6rem 1rem; }
    .metricas b { color: var(--azul); font-size: 1.15rem; }
    h3 { margin: 1.2rem 0 .5rem; }
  `],
  template: `
    <div class="card">
      <div class="topo">
        <div>
          <h2 style="margin:0">{{ b.alunoNome }}</h2>
          <div class="sub">{{ b.turma }} · {{ b.trimestre }}º trimestre de {{ b.ano }}
            ({{ b.periodoInicio | date:'dd/MM/yyyy' }} a {{ b.periodoFim | date:'dd/MM/yyyy' }})</div>
        </div>
        <button class="btn btn-outline" (click)="baixarPdf()">📄 Baixar PDF</button>
      </div>

      <div class="metricas">
        <div class="box">Média das notas<br><b>{{ b.mediaNotas }}</b></div>
        <div class="box">Aproveitamento<br><b>{{ b.aproveitamentoPct }}%</b></div>
        <div class="box">Presença<br><b>{{ b.frequencia.percentualPresenca }}%</b></div>
      </div>

      <h3>Provas</h3>
      @if (b.provas.length === 0) {
        <p class="muted">Nenhuma prova cadastrada no período.</p>
      } @else {
        <div class="tabela-scroll">
          <table class="tabela">
            <thead><tr><th>Prova</th><th>Data</th><th class="num">Nota</th><th class="num">Máx.</th><th class="num">Aproveitamento</th></tr></thead>
            <tbody>
              @for (p of b.provas; track p.titulo + p.data) {
                <tr>
                  <td>{{ p.titulo }}</td>
                  <td>{{ p.data | date:'dd/MM/yyyy' }}</td>
                  <td class="num">{{ p.nota != null ? p.nota : '—' }}</td>
                  <td class="num">{{ p.notaMaxima }}</td>
                  <td class="num">{{ p.percentual != null ? p.percentual + '%' : '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <h3>Frequência</h3>
      <div class="tabela-scroll">
        <table class="tabela">
          <thead><tr><th class="num">Aulas</th><th class="num">Presenças</th><th class="num">Faltas</th>
            <th class="num">% Presença</th><th class="num">Bíblia</th><th class="num">Revista</th><th class="num">Lição</th></tr></thead>
          <tbody>
            <tr>
              <td class="num">{{ b.frequencia.totalAulas }}</td>
              <td class="num">{{ b.frequencia.presencas }}</td>
              <td class="num">{{ b.frequencia.faltas }}</td>
              <td class="num">{{ b.frequencia.percentualPresenca }}%</td>
              <td class="num">{{ b.frequencia.biblias }}</td>
              <td class="num">{{ b.frequencia.revistas }}</td>
              <td class="num">{{ b.frequencia.licoes }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      @if (b.visitantesTrazidos) {
        <p class="muted" style="margin-top:1rem">Visitantes trazidos no período: <b>{{ b.visitantesTrazidos }}</b></p>
      }
    </div>
  `,
})
export class BoletimViewComponent {
  @Input({ required: true }) b!: BoletimResponse;

  baixarPdf(): void {
    exportarBoletimPdf(this.b);
  }
}
