import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { DashboardResponse } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe],
  styles: [`
    .saudacao { margin-bottom: 1.5rem; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .stat { background: var(--superficie); border-radius: var(--raio); box-shadow: var(--sombra); padding: 1.1rem 1.25rem; }
    .stat .n { font-size: 1.9rem; font-weight: 800; color: var(--titulo); line-height: 1.1; }
    .stat .rot { color: var(--cinza-texto); font-size: .82rem; margin-top: .2rem; }
    .graficos { display: grid; grid-template-columns: 1fr; gap: 1.25rem; margin-bottom: 1.75rem; }
    @media (min-width: 900px) { .graficos { grid-template-columns: 3fr 2fr; } }
    .card h3 { margin: 0 0 .2rem; font-size: 1.05rem; }
    .card .sub { color: var(--cinza-texto); font-size: .82rem; margin-bottom: .8rem; }
    svg { width: 100%; height: auto; display: block; }
    .barra rect { transition: opacity .1s; }
    .barra:hover rect.fill { opacity: .82; }
    .eixo { fill: var(--cinza-texto); font-size: 9px; }
    .grade { stroke: var(--cinza-borda); stroke-width: 1; }
    .meta { stroke: var(--dourado); stroke-width: 1.5; stroke-dasharray: 4 3; }
    .val { fill: var(--texto); font-size: 9px; font-weight: 700; text-anchor: middle; }
    /* distribuição */
    .dist-legenda { display: flex; flex-direction: column; gap: .5rem; margin-top: 1rem; }
    .dist-legenda .item { display: flex; align-items: center; gap: .5rem; font-size: .9rem; }
    .dist-legenda .dot { width: 12px; height: 12px; border-radius: 3px; flex: 0 0 auto; }
    .dist-legenda .qtd { margin-left: auto; font-weight: 700; color: var(--titulo); }
    .atalhos { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; }
    .atalho { background: var(--superficie); border-radius: var(--raio); box-shadow: var(--sombra);
              padding: 1.3rem; text-decoration: none; color: inherit; transition: transform .1s, box-shadow .1s;
              border-top: 3px solid var(--dourado); }
    .atalho:hover { transform: translateY(-3px); box-shadow: var(--sombra-md); text-decoration: none; }
    .atalho .ico { font-size: 1.7rem; }
    .atalho h3 { margin: .5rem 0 .25rem; }
    .atalho p { margin: 0; color: var(--cinza-texto); font-size: .85rem; }
  `],
  template: `
    <div class="saudacao">
      <h2>Olá, {{ auth.username() }}! 👋</h2>
      <p class="muted">Painel da Escola Bíblica Dominical — {{ classeCtx.nomeSelecionada() || 'todas as turmas' }}.</p>
    </div>

    @if (dados(); as d) {
      <div class="stats">
        <div class="stat"><div class="n">{{ d.totalAlunos }}</div><div class="rot">Alunos ativos</div></div>
        <div class="stat"><div class="n">{{ d.totalAulas }}</div><div class="rot">Aulas registradas</div></div>
        <div class="stat"><div class="n">{{ d.presencaMediaPct }}%</div><div class="rot">Presença média</div></div>
        <div class="stat"><div class="n">{{ d.totalProvas }}</div><div class="rot">Provas cadastradas</div></div>
      </div>

      <div class="graficos">
        <!-- Frequência por aula -->
        <div class="card">
          <h3>Frequência por aula</h3>
          <div class="sub">Presentes sobre o total de alunos ativos (últimas {{ barras().length }} aulas). Linha tracejada = meta 75%.</div>
          @if (barras().length === 0) {
            <p class="muted text-center" style="padding:1.5rem 0">Ainda não há aulas com chamada.</p>
          } @else {
            <svg [attr.viewBox]="'0 0 ' + vbW() + ' 172'" role="img"
                 aria-label="Gráfico de frequência por aula">
              <!-- grades 0/25/50/75/100 -->
              @for (g of grades; track g) {
                <line class="grade" x1="24" [attr.x2]="vbW()" [attr.y1]="yDe(g)" [attr.y2]="yDe(g)"
                      [attr.opacity]="g === 0 ? 0.6 : 0.35" />
                <text class="eixo" x="20" [attr.y]="yDe(g) + 3" text-anchor="end">{{ g }}</text>
              }
              <!-- meta 75% -->
              <line class="meta" x1="24" [attr.x2]="vbW()" [attr.y1]="yDe(75)" [attr.y2]="yDe(75)" />
              <!-- barras -->
              @for (b of barras(); track b.i) {
                <g class="barra">
                  <title>{{ b.data | date:'dd/MM/yyyy' }}{{ b.tema ? ' — ' + b.tema : '' }}: {{ b.presentes }}/{{ b.total }} ({{ b.pct }}%)</title>
                  <rect class="fill" [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h"
                        rx="3" fill="var(--verde)" />
                  <text class="val" [attr.x]="b.x + b.w / 2" [attr.y]="b.y - 3">{{ b.pct }}</text>
                  <text class="eixo" [attr.x]="b.x + b.w / 2" y="168" text-anchor="middle">{{ b.data | date:'dd/MM' }}</text>
                </g>
              }
            </svg>
          }
        </div>

        <!-- Distribuição de frequência -->
        <div class="card">
          <h3>Distribuição de frequência</h3>
          <div class="sub">Quantos alunos em cada faixa de presença.</div>
          @if (d.totalAlunos === 0) {
            <p class="muted text-center" style="padding:1.5rem 0">Nenhum aluno ativo.</p>
          } @else {
            <svg viewBox="0 0 200 26" role="img" aria-label="Distribuição de frequência dos alunos">
              @for (s of segmentos(); track s.rot) {
                <rect [attr.x]="s.x" y="3" [attr.width]="s.w" height="20" rx="4" [attr.fill]="s.cor" />
              }
            </svg>
            <div class="dist-legenda">
              @for (s of segmentos(); track s.rot) {
                <div class="item">
                  <span class="dot" [style.background]="s.cor"></span>
                  <span>{{ s.rot }}</span>
                  <span class="qtd">{{ s.n }}</span>
                </div>
              }
            </div>
          }
        </div>
      </div>
    }

    <div class="atalhos">
      <a class="atalho" routerLink="/chamada"><div class="ico">✅</div><h3>Fazer chamada</h3>
        <p>Registre presença, Bíblia, revista e lição da aula.</p></a>
      <a class="atalho" routerLink="/desafios"><div class="ico">🏆</div><h3>Ver rankings</h3>
        <p>Acompanhe os destaques da classe nos desafios.</p></a>
      <a class="atalho" routerLink="/relatorio"><div class="ico">📊</div><h3>Relatório de presenças</h3>
        <p>Resumo de frequência por aluno e período.</p></a>
      <a class="atalho" routerLink="/provas"><div class="ico">📝</div><h3>Provas e notas</h3>
        <p>Cadastre provas e lance as notas dos alunos.</p></a>
    </div>
  `,
})
export class DashboardComponent {
  private api = inject(ApiService);
  auth = inject(AuthService);
  classeCtx = inject(ClasseContextService);

  dados = signal<DashboardResponse | null>(null);
  readonly grades = [0, 25, 50, 75, 100];

  // geometria do gráfico de barras
  private readonly PLOT_TOP = 10;
  private readonly PLOT_H = 130;
  private readonly SLOT = 44;

  vbW = computed(() => Math.max(240, (this.dados()?.frequenciaPorAula.length ?? 0) * this.SLOT + 28));

  barras = computed(() => {
    const pts = this.dados()?.frequenciaPorAula ?? [];
    const base = this.PLOT_TOP + this.PLOT_H;
    const bw = Math.min(28, this.SLOT * 0.55);
    return pts.map((p, i) => {
      const h = Math.max(1.5, (p.pct / 100) * this.PLOT_H);
      return { i, x: 28 + i * this.SLOT + (this.SLOT - bw) / 2, y: base - h, w: bw, h,
               pct: p.pct, presentes: p.presentes, total: p.total, data: p.data, tema: p.tema };
    });
  });

  segmentos = computed(() => {
    const dist = this.dados()?.distribuicao;
    if (!dist) { return []; }
    const total = dist.excelente + dist.boa + dist.atencao || 1;
    const defs = [
      { rot: 'Excelente (≥90%)', n: dist.excelente, cor: 'var(--verde)' },
      { rot: 'Boa (75–89%)', n: dist.boa, cor: 'var(--dourado)' },
      { rot: 'Atenção (<75%)', n: dist.atencao, cor: 'var(--vermelho)' },
    ];
    let x = 0;
    return defs.map((s) => {
      const w = (s.n / total) * 200;
      const seg = { ...s, x, w: w > 0 ? Math.max(w - 2, 0) : 0 };
      x += w;
      return seg;
    });
  });

  yDe(v: number): number {
    return this.PLOT_TOP + this.PLOT_H - (v / 100) * this.PLOT_H;
  }

  constructor() {
    effect(() => { this.classeCtx.selecionadaId(); this.carregar(); }, { allowSignalWrites: true });
  }

  private carregar(): void {
    this.api.dashboard(this.classeCtx.selecionadaId()).subscribe({
      next: (d) => this.dados.set(d),
      error: () => {},
    });
  }
}
