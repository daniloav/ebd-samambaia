import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { Classe, RelatorioMensalResponse } from '../../core/models';
import { exportarExcel, exportarPdf } from '../../core/export.util';
import { TelemetriaService } from '../../core/telemetria.service';

/** Cores das turmas no gráfico — tons médios, visíveis tanto no tema claro quanto no escuro. */
const CORES = ['#3b82f6', '#eab308', '#22c55e', '#ef4444', '#a855f7', '#14b8a6', '#f97316', '#64748b'];

const MESES = [
  { v: 1, n: 'Janeiro' }, { v: 2, n: 'Fevereiro' }, { v: 3, n: 'Março' }, { v: 4, n: 'Abril' },
  { v: 5, n: 'Maio' }, { v: 6, n: 'Junho' }, { v: 7, n: 'Julho' }, { v: 8, n: 'Agosto' },
  { v: 9, n: 'Setembro' }, { v: 10, n: 'Outubro' }, { v: 11, n: 'Novembro' }, { v: 12, n: 'Dezembro' },
];

@Component({
  selector: 'app-relatorio-mensal',
  standalone: true,
  imports: [FormsModule],
  styles: [`
    .filtros { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; }
    .filtros .form-group { margin: 0; }
    .turmas { border: 1px solid var(--cinza-borda, #e2e8f0); border-radius: 8px; padding: .6rem .8rem;
              display: flex; gap: .4rem 1.2rem; flex-wrap: wrap; max-height: 8.5rem; overflow: auto; }
    .turmas label { display: flex; align-items: center; gap: .4rem; font-size: .9rem; margin: 0; cursor: pointer; }
    .turmas label.todas { font-weight: 700; padding-right: 1rem; border-right: 1px solid var(--cinza-borda, #e2e8f0); }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .kpis .card { text-align: center; }
    .kpis strong { font-size: 1.5rem; display: block; }
    .num { text-align: center; }
    .pct { font-weight: 700; }
    .pct.bom { color: var(--verde-texto, #276749); }
    .pct.medio { color: var(--alerta); }
    .pct.ruim { color: var(--vermelho, #c53030); }

    /* Gráfico de barras (CSS puro, sem bibliotecas) */
    .legenda { display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: .8rem; font-size: .85rem; }
    .legenda span { display: flex; align-items: center; gap: .35rem; }
    .legenda i { width: .8rem; height: .8rem; border-radius: 3px; display: inline-block; }
    .plot { display: flex; gap: .5rem; }
    .eixo-y { display: flex; flex-direction: column; justify-content: space-between; height: 200px;
              font-size: .7rem; color: var(--cinza-texto, #718096); text-align: right; min-width: 2.2rem; }
    .colunas { flex: 1; display: flex; align-items: flex-end; gap: .5rem; height: 200px;
               border-left: 1px solid var(--cinza-borda, #e2e8f0); border-bottom: 1px solid var(--cinza-borda, #e2e8f0);
               background: repeating-linear-gradient(to top, transparent 0 49px, rgba(127, 145, 170, .25) 49px 50px);
               padding: 0 .4rem; overflow-x: auto; }
    .grupo { flex: 1 1 0; min-width: 2.5rem; max-width: 7rem; display: flex; flex-direction: column; justify-content: flex-end; height: 100%; }
    .barras { display: flex; align-items: flex-end; justify-content: center; gap: 2px; height: 100%; }
    .barra { flex: 1 1 0; max-width: 2rem; min-height: 2px; border-radius: 3px 3px 0 0; }
    .barra.zero { background-image: repeating-linear-gradient(45deg, #cbd5e0 0 3px, #e2e8f0 3px 6px) !important; }
    .rotulos { display: flex; gap: .5rem; padding: .3rem .4rem 0; }
    .rotulos div { flex: 1 1 0; min-width: 2.5rem; max-width: 7rem; text-align: center; font-size: .7rem; color: var(--cinza-texto, #718096); }
    .rotulos b { display: block; color: var(--texto, #1a202c); font-size: .75rem; }
  `],
  template: `
    <h2>Relatório de presença por mês</h2>
    <p class="muted">Consolidado do mês por turma — escolha uma, várias ou todas as turmas.</p>

    <div class="card" style="margin-bottom:1.5rem">
      <div class="filtros" style="margin-bottom:1rem">
        <div class="form-group"><label for="rm-mes">Mês</label>
          <select id="rm-mes" aria-label="Mês do relatório" [(ngModel)]="mes">
            <option [ngValue]="null">Ano inteiro</option>
            @for (m of meses; track m.v) { <option [ngValue]="m.v">{{ m.n }}</option> }
          </select>
        </div>
        <div class="form-group"><label for="rm-ano">Ano</label>
          <input id="rm-ano" aria-label="Ano do relatório" type="number" min="2000" max="2100"
                 [(ngModel)]="ano" style="width:100px" />
        </div>
        <button class="btn btn-verde" (click)="gerar()" [disabled]="carregando()">
          {{ carregando() ? 'Gerando...' : 'Gerar relatório' }}
        </button>
        @if (dados()?.porTurma?.length) {
          <button class="btn btn-outline" (click)="exportarPdf()">📄 PDF</button>
          <button class="btn btn-outline" (click)="exportarExcel()">📊 Excel</button>
        }
      </div>

      <div class="form-group" style="margin:0">
        <label id="rm-turmas-label">Turmas</label>
        @if (classes().length) {
          <div class="turmas" role="group" aria-labelledby="rm-turmas-label">
            <label class="todas">
              <input type="checkbox" aria-label="Selecionar todas as turmas"
                     [checked]="todas()" (change)="alternarTodas($any($event.target).checked)" />
              Todas ({{ classes().length }})
            </label>
            @for (c of classes(); track c.id) {
              <label>
                <input type="checkbox" [attr.aria-label]="'Turma ' + c.nome"
                       [checked]="selecionadas().includes(c.id)" (change)="alternar(c.id)" />
                {{ c.nome }}
              </label>
            }
          </div>
        } @else {
          <p class="muted">Nenhuma turma disponível.</p>
        }
      </div>
    </div>

    @if (dados(); as d) {
      @if (!d.turmas.length) {
        <div class="card"><p class="muted text-center">Selecione ao menos uma turma.</p></div>
      } @else {
        <div class="kpis">
          <div class="card"><div class="muted">Aulas</div><strong>{{ d.totais.aulas }}</strong>
            <span class="muted" style="font-size:.75rem">{{ d.totais.aulasComChamada }} com chamada</span></div>
          <div class="card"><div class="muted">% Presença</div>
            <strong [class]="'pct ' + faixa(d.totais.percentualPresenca)">{{ d.totais.percentualPresenca }}%</strong></div>
          <div class="card"><div class="muted">Presenças</div><strong style="color:#2f855a">{{ d.totais.presencas }}</strong></div>
          <div class="card"><div class="muted">Faltas</div><strong style="color:#c53030">{{ d.totais.faltas }}</strong>
            <span class="muted" style="font-size:.75rem">{{ d.totais.faltasJustificadas }} justificada(s)</span></div>
          <div class="card"><div class="muted">Bíblias</div><strong>{{ d.totais.biblias }}</strong></div>
          <div class="card"><div class="muted">Revistas</div><strong>{{ d.totais.revistas }}</strong></div>
          <div class="card"><div class="muted">Lições</div><strong>{{ d.totais.licoes }}</strong></div>
          <div class="card"><div class="muted">Visitantes</div><strong style="color:var(--alerta)">{{ d.totais.visitantes }}</strong></div>
        </div>

        <div class="card" style="margin-bottom:1.5rem">
          <h3 style="margin-top:0">Por turma — {{ d.periodoLabel }}</h3>
          <div class="tabela-scroll">
            <table class="tabela">
              <thead>
                <tr>
                  <th>Turma</th><th class="num">Alunos</th><th class="num">Aulas</th>
                  <th class="num">Presenças</th><th class="num">Faltas</th><th class="num">% Presença</th>
                  <th class="num">Bíblias</th><th class="num">Revistas</th><th class="num">Lições</th><th class="num">Visitantes</th>
                </tr>
              </thead>
              <tbody>
                @for (t of d.porTurma; track t.classeId) {
                  <tr>
                    <td><span class="legenda"><i [style.background]="cor(t.classeId)" aria-hidden="true"></i><strong>{{ t.classeNome }}</strong></span></td>
                    <td class="num">{{ t.totais.alunosAtivos }}</td>
                    <td class="num">{{ t.totais.aulas }}</td>
                    <td class="num">{{ t.totais.presencas }}</td>
                    <td class="num">{{ t.totais.faltas }}</td>
                    <td class="num pct" [class]="'num pct ' + faixa(t.totais.percentualPresenca)">{{ t.totais.percentualPresenca }}%</td>
                    <td class="num">{{ t.totais.biblias }}</td>
                    <td class="num">{{ t.totais.revistas }}</td>
                    <td class="num">{{ t.totais.licoes }}</td>
                    <td class="num">{{ t.totais.visitantes }}</td>
                  </tr>
                }
              </tbody>
              <tfoot>
                <tr style="font-weight:bold;border-top:2px solid #e2e8f0">
                  <td>Total ({{ d.porTurma.length }} turma(s))</td>
                  <td class="num">{{ d.totais.alunosAtivos }}</td>
                  <td class="num">{{ d.totais.aulas }}</td>
                  <td class="num">{{ d.totais.presencas }}</td>
                  <td class="num">{{ d.totais.faltas }}</td>
                  <td class="num">{{ d.totais.percentualPresenca }}%</td>
                  <td class="num">{{ d.totais.biblias }}</td>
                  <td class="num">{{ d.totais.revistas }}</td>
                  <td class="num">{{ d.totais.licoes }}</td>
                  <td class="num">{{ d.totais.visitantes }}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>

        <div class="card">
          <h3 style="margin-top:0">
            {{ d.mes ? 'Presença por domingo' : 'Presença por mês' }} — {{ d.periodoLabel }}
          </h3>
          @if (!d.serie.length) {
            <p class="muted text-center">Nenhuma aula registrada no período.</p>
          } @else {
            <div class="legenda">
              @for (t of d.porTurma; track t.classeId) {
                <span><i [style.background]="cor(t.classeId)" aria-hidden="true"></i>{{ t.classeNome }}</span>
              }
            </div>
            <div class="plot" role="img" [attr.aria-label]="resumoGrafico(d)">
              <div class="eixo-y" aria-hidden="true"><span>100%</span><span>75%</span><span>50%</span><span>25%</span><span>0%</span></div>
              <div style="flex:1;min-width:0">
                <div class="colunas">
                  @for (p of d.serie; track p.data) {
                    <div class="grupo">
                      <div class="barras">
                        @for (v of p.porTurma; track v.classeId) {
                          <div class="barra" [class.zero]="v.presencas + v.faltas === 0"
                               [style.height.%]="v.presencas + v.faltas === 0 ? 3 : v.percentualPresenca"
                               [style.background]="cor(v.classeId)"
                               [title]="v.classeNome + ' — ' + p.rotulo + ': ' +
                                 (v.presencas + v.faltas === 0 ? 'sem chamada' :
                                  v.percentualPresenca + '% (' + v.presencas + ' de ' + (v.presencas + v.faltas) + ')')"></div>
                        }
                      </div>
                    </div>
                  }
                </div>
                <div class="rotulos" aria-hidden="true">
                  @for (p of d.serie; track p.data) {
                    <div><b>{{ p.totais.percentualPresenca }}%</b>{{ p.rotulo }}</div>
                  }
                </div>
              </div>
            </div>
            <p class="muted" style="font-size:.8rem;margin:.8rem 0 0">
              Cada barra é o <b>% de presença</b> da turma ({{ d.mes ? 'no domingo' : 'no mês' }});
              o número acima do grupo é o consolidado. Barra hachurada = aula sem chamada registrada.
              Aulas adiadas não entram.
            </p>
          }
        </div>
      }
    } @else if (carregando()) {
      <div class="card"><div class="spinner-wrap muted">Gerando...</div></div>
    }
  `,
})
export class RelatorioMensalComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private telemetria = inject(TelemetriaService);

  readonly meses = MESES;
  ano = new Date().getFullYear();
  mes: number | null = new Date().getMonth() + 1;

  classes = signal<Classe[]>([]);
  selecionadas = signal<number[]>([]);
  dados = signal<RelatorioMensalResponse | null>(null);
  carregando = signal(false);

  /** Todas marcadas = nenhuma restrição (o backend assume "todas as permitidas"). */
  todas = computed(() => this.classes().length > 0 && this.selecionadas().length === this.classes().length);

  constructor() {
    this.api.listarClasses(true).subscribe({
      next: (cs) => {
        this.classes.set(cs);
        this.selecionadas.set(cs.map((c) => c.id)); // começa com todas
        this.gerar();
      },
      error: () => this.toast.erro('Falha ao carregar as turmas.'),
    });
  }

  alternar(id: number): void {
    const atual = this.selecionadas();
    this.selecionadas.set(atual.includes(id) ? atual.filter((x) => x !== id) : [...atual, id]);
  }

  alternarTodas(marcar: boolean): void {
    this.selecionadas.set(marcar ? this.classes().map((c) => c.id) : []);
  }

  gerar(): void {
    if (!this.selecionadas().length) {
      this.toast.erro('Escolha ao menos uma turma.');
      return;
    }
    this.carregando.set(true);
    // Ordem das turmas do relatório = ordem da lista (mantém as cores estáveis entre gerações).
    const ids = this.classes().map((c) => c.id).filter((id) => this.selecionadas().includes(id));
    this.api.relatorioMensal(this.ano, this.mes, ids).subscribe({
      next: (r) => { this.dados.set(r); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao gerar o relatório.'); this.carregando.set(false); },
    });
  }

  /** Cor fixa por turma (pela posição na lista de turmas), para tabela e gráfico casarem. */
  cor(classeId: number): string {
    const i = this.classes().findIndex((c) => c.id === classeId);
    return CORES[(i < 0 ? 0 : i) % CORES.length];
  }

  faixa(pct: number): string {
    return pct >= 75 ? 'bom' : pct >= 50 ? 'medio' : 'ruim';
  }

  /** Texto alternativo do gráfico (leitores de tela). */
  resumoGrafico(d: RelatorioMensalResponse): string {
    const pontos = d.serie.map((p) => `${p.rotulo}: ${p.totais.percentualPresenca}%`).join('; ');
    return `Percentual de presença ${d.mes ? 'por domingo' : 'por mês'} em ${d.periodoLabel}. ${pontos}`;
  }

  private cols(): string[] {
    return ['Turma', 'Alunos', 'Aulas', 'Aulas com chamada', 'Presenças', 'Faltas', 'Faltas justificadas',
      '% Presença', 'Bíblias', 'Revistas', 'Lições', 'Visitantes'];
  }

  private linhas(d: RelatorioMensalResponse): (string | number)[][] {
    const rows: (string | number)[][] = d.porTurma.map((t) => [
      t.classeNome, t.totais.alunosAtivos, t.totais.aulas, t.totais.aulasComChamada,
      t.totais.presencas, t.totais.faltas, t.totais.faltasJustificadas,
      `${t.totais.percentualPresenca}%`, t.totais.biblias, t.totais.revistas, t.totais.licoes, t.totais.visitantes,
    ]);
    rows.push(['TOTAL', d.totais.alunosAtivos, d.totais.aulas, d.totais.aulasComChamada,
      d.totais.presencas, d.totais.faltas, d.totais.faltasJustificadas,
      `${d.totais.percentualPresenca}%`, d.totais.biblias, d.totais.revistas, d.totais.licoes, d.totais.visitantes]);
    // A série (gráfico) também vai na exportação, como linhas de detalhe.
    rows.push([]);
    rows.push([d.mes ? 'Domingo' : 'Mês', '', '', '', 'Presenças', 'Faltas', '', '% Presença', '', '', '', '']);
    for (const p of d.serie) {
      rows.push([p.rotulo, '', '', '', p.totais.presencas, p.totais.faltas, '',
        `${p.totais.percentualPresenca}%`, '', '', '', '']);
    }
    return rows;
  }

  async exportarPdf(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    this.telemetria.clique('relatorio-mensal-pdf');
    await exportarPdf(`relatorio-presenca-${d.ano}${d.mes ? '-' + String(d.mes).padStart(2, '0') : ''}`,
      'Relatório de presença — EBD ICES',
      `${d.periodoLabel} · ${d.turmas.length} turma(s) · ${d.totais.percentualPresenca}% de presença`,
      this.cols(), this.linhas(d));
  }

  async exportarExcel(): Promise<void> {
    const d = this.dados(); if (!d) { return; }
    this.telemetria.clique('relatorio-mensal-excel');
    await exportarExcel(`relatorio-presenca-${d.ano}${d.mes ? '-' + String(d.mes).padStart(2, '0') : ''}`,
      'Presença por turma', this.cols(), this.linhas(d));
  }
}
