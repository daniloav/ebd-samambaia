import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { UsoResponse } from '../../core/models';

/** Painel de estatísticas de uso / engajamento (ADMIN). Itens A–G do roadmap (quick win). */
@Component({
  selector: 'app-uso',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  styles: [`
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .kpi { background: var(--azul); color: #fff; border-radius: 12px; padding: 1rem 1.1rem; }
    .kpi .n { font-size: 2rem; font-weight: 800; line-height: 1.1; }
    .kpi .l { font-size: .78rem; opacity: .9; margin-top: .2rem; }
    .kpi.verde { background: #166534; }
    .kpi.roxo { background: #5b21b6; }
    .kpi.laranja { background: #9a3412; }
    .grid2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1.25rem; }
    .subt { font-weight: 700; margin: 0 0 .8rem; }
    .live { display: inline-block; width: .6rem; height: .6rem; border-radius: 50%; background: #22c55e; margin-right: .4rem; vertical-align: middle; }
    .bars { display: flex; flex-direction: column; gap: .4rem; }
    .bar-row { display: grid; grid-template-columns: 3.2rem 1fr 2.5rem; align-items: center; gap: .5rem; font-size: .8rem; }
    .bar-track { background: var(--cinza-borda, #e5e7eb); border-radius: 6px; height: .95rem; overflow: hidden; }
    .bar-fill { background: var(--azul); height: 100%; border-radius: 6px; min-width: 2px; }
    .bar-fill.verde { background: #16a34a; }
    .bar-val { text-align: right; color: var(--cinza-texto); }
    .pill { font-size: .72rem; font-weight: 700; padding: .12rem .5rem; border-radius: 999px; background: #dbeafe; color: #1e40af; white-space: nowrap; }
    .vazio { color: var(--cinza-texto); font-size: .85rem; }
    .barcols { display: flex; align-items: flex-end; gap: 3px; height: 120px; }
    .barcols .col { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; }
    .barcols .col .b { width: 100%; background: var(--azul); border-radius: 3px 3px 0 0; min-height: 2px; }
    .barcols .col .cap { font-size: .6rem; color: var(--cinza-texto); margin-top: .2rem; white-space: nowrap; }
  `],
  template: `
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:.5rem">
      <div>
        <h2 style="margin-bottom:.2rem">Uso &amp; engajamento</h2>
        <p class="muted" style="margin:0">Como a escola está usando o app. Atualizado ao abrir.</p>
      </div>
      <button class="btn btn-outline" (click)="carregar()" [disabled]="carregando()">
        {{ carregando() ? 'Atualizando…' : '↻ Atualizar' }}
      </button>
    </div>

    @if (carregando() && !d()) {
      <div class="spinner-wrap muted" style="padding:2rem">Carregando…</div>
    }
    @if (d(); as u) {
      <!-- KPIs principais -->
      <div class="kpis" style="margin-top:1.25rem">
        <div class="kpi verde"><div class="n"><span class="live"></span>{{ u.onlineAgora }}</div><div class="l">Online agora (últ. 15 min)</div></div>
        <div class="kpi"><div class="n">{{ u.acessosHoje }}</div><div class="l">Acessos hoje</div></div>
        <div class="kpi roxo"><div class="n">{{ u.ativosHoje }}</div><div class="l">Pessoas ativas hoje</div></div>
        <div class="kpi laranja"><div class="n">{{ u.taxaAtivacaoPct | number:'1.0-0' }}%</div><div class="l">Já acessaram ao menos 1×</div></div>
      </div>

      <div class="grid2">
        <!-- A) Quem está online -->
        <div class="card">
          <p class="subt"><span class="live"></span>Quem está online</p>
          @if (u.online.length === 0) {
            <p class="vazio">Ninguém ativo nos últimos 15 minutos.</p>
          } @else {
            <div class="bars">
              @for (o of u.online; track o.username) {
                <div style="display:flex;justify-content:space-between;gap:.5rem;align-items:center">
                  <span><strong>{{ o.username }}</strong> <span class="pill">{{ o.papel }}</span></span>
                  <span class="muted" style="font-size:.78rem">{{ o.ultimoAcesso | date:'HH:mm' }}</span>
                </div>
              }
            </div>
          }
        </div>

        <!-- B) Volume de acessos -->
        <div class="card">
          <p class="subt">Volume de acessos</p>
          <div class="bars">
            <div class="bar-row"><span>Hoje</span><span class="muted">logins / pessoas</span><span class="bar-val">{{ u.acessosHoje }}/{{ u.ativosHoje }}</span></div>
            <div class="bar-row"><span>7 dias</span><span class="muted">logins / pessoas</span><span class="bar-val">{{ u.acessos7d }}/{{ u.ativos7d }}</span></div>
            <div class="bar-row"><span>30 dias</span><span class="muted">logins / pessoas</span><span class="bar-val">{{ u.acessos30d }}/{{ u.ativos30d }}</span></div>
          </div>
          <p class="muted" style="font-size:.75rem;margin-top:.6rem">DAU/WAU/MAU = pessoas únicas ativas por dia/semana/mês.</p>
        </div>

        <!-- C) Adoção / ativação -->
        <div class="card">
          <p class="subt">Adoção</p>
          <div class="bars">
            <div class="bar-row"><span>Contas</span>
              <div class="bar-track"><div class="bar-fill verde" [style.width.%]="perc(u.usuariosComAcesso, u.totalUsuarios)"></div></div>
              <span class="bar-val">{{ u.usuariosComAcesso }}/{{ u.totalUsuarios }}</span></div>
            <div class="bar-row"><span>Alunos</span>
              <div class="bar-track"><div class="bar-fill verde" [style.width.%]="perc(u.alunosAtivados, u.alunosTotal)"></div></div>
              <span class="bar-val">{{ u.alunosAtivados }}/{{ u.alunosTotal }}</span></div>
          </div>
          <p class="muted" style="font-size:.78rem;margin-top:.6rem">
            <strong>{{ u.usuariosNuncaAcessaram }}</strong> conta(s) nunca acessaram ·
            aluno ativado = já logou e trocou a senha padrão.
          </p>
        </div>

        <!-- G) Dispositivos -->
        <div class="card">
          <p class="subt">Dispositivos (30 dias)</p>
          @if (u.dispositivos.length === 0) {
            <p class="vazio">Sem acessos registrados ainda.</p>
          } @else {
            <div class="bars">
              @for (dv of u.dispositivos; track dv.rotulo) {
                <div class="bar-row">
                  <span style="grid-column:span 1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ dv.rotulo }}</span>
                  <div class="bar-track"><div class="bar-fill" [style.width.%]="perc(dv.quantidade, totalDispositivos())"></div></div>
                  <span class="bar-val">{{ dv.quantidade }}</span>
                </div>
              }
            </div>
          }
        </div>
      </div>

      <!-- B) Série diária (14 dias) -->
      <div class="card" style="margin-top:1.25rem">
        <p class="subt">Acessos por dia (14 dias)</p>
        <div class="barcols">
          @for (p of u.serieDiaria; track p.data) {
            <div class="col" [title]="(p.data | date:'dd/MM') + ': ' + p.acessos + ' acessos, ' + p.ativos + ' pessoas'">
              <div class="b" [style.height.%]="altura(p.acessos, maxSerie())"></div>
              <div class="cap">{{ p.data | date:'dd' }}</div>
            </div>
          }
        </div>
      </div>

      <div class="grid2" style="margin-top:1.25rem">
        <!-- Distribuição por hora -->
        <div class="card">
          <p class="subt">Acessos por hora do dia (30 dias)</p>
          <div class="barcols">
            @for (h of u.porHora; track $index) {
              <div class="col" [title]="$index + 'h: ' + h + ' acessos'">
                <div class="b" [style.height.%]="altura(h, maxHora())"></div>
                <div class="cap">{{ rotuloHora($index) }}</div>
              </div>
            }
          </div>
        </div>

        <!-- Distribuição por dia da semana -->
        <div class="card">
          <p class="subt">Acessos por dia da semana (30 dias)</p>
          <div class="barcols">
            @for (ds of u.porDiaSemana; track $index) {
              <div class="col" [title]="diaSemana($index) + ': ' + ds + ' acessos'">
                <div class="b" [style.height.%]="altura(ds, maxDiaSemana())"></div>
                <div class="cap">{{ diaSemana($index) }}</div>
              </div>
            }
          </div>
        </div>
      </div>

      <div class="grid2" style="margin-top:1.25rem">
        <!-- E/F) Mais ativos -->
        <div class="card">
          <p class="subt">Mais ativos (30 dias)</p>
          @if (u.maisAtivos.length === 0) {
            <p class="vazio">Sem dados ainda.</p>
          } @else {
            <div class="bars">
              @for (m of u.maisAtivos; track m.username) {
                <div class="bar-row">
                  <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ m.username }}</span>
                  <div class="bar-track"><div class="bar-fill" [style.width.%]="perc(m.acessos, u.maisAtivos[0].acessos)"></div></div>
                  <span class="bar-val">{{ m.acessos }}</span>
                </div>
              }
            </div>
          }
        </div>

        <!-- E) Dormentes -->
        <div class="card">
          <p class="subt">Sumiram (14+ dias sem acessar)</p>
          @if (u.dormentes.length === 0) {
            <p class="vazio">Ninguém dormente — 🎉</p>
          } @else {
            <div class="bars">
              @for (dm of u.dormentes; track dm.username) {
                <div style="display:flex;justify-content:space-between;gap:.5rem;align-items:center">
                  <span><strong>{{ dm.username }}</strong> <span class="pill">{{ dm.papel }}</span></span>
                  <span class="muted" style="font-size:.78rem">últ.: {{ dm.ultimoAcesso | date:'dd/MM' }}</span>
                </div>
              }
            </div>
          }
        </div>
      </div>
    }
    @if (!carregando() && !d()) {
      <p class="muted" style="margin-top:1.5rem">Não foi possível carregar as estatísticas.</p>
    }
  `,
})
export class UsoComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  d = signal<UsoResponse | null>(null);
  carregando = signal(false);

  private readonly DIAS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

  totalDispositivos = computed(() =>
    (this.d()?.dispositivos ?? []).reduce((acc, x) => acc + x.quantidade, 0));
  maxSerie = computed(() =>
    Math.max(1, ...((this.d()?.serieDiaria ?? []).map((p) => p.acessos))));
  maxHora = computed(() => Math.max(1, ...((this.d()?.porHora ?? []))));
  maxDiaSemana = computed(() => Math.max(1, ...((this.d()?.porDiaSemana ?? []))));

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.uso().subscribe({
      next: (r) => { this.d.set(r); this.carregando.set(false); },
      error: () => { this.carregando.set(false); this.toast.erro('Falha ao carregar as estatísticas.'); },
    });
  }

  perc(parte: number, total: number): number {
    return total > 0 ? Math.round((parte / total) * 100) : 0;
  }

  altura(valor: number, max: number): number {
    return max > 0 ? Math.max(2, Math.round((valor / max) * 100)) : 2;
  }

  diaSemana(i: number): string {
    return this.DIAS[i] ?? String(i);
  }

  rotuloHora(i: number): string {
    return i % 3 === 0 ? String(i) : '';
  }
}
