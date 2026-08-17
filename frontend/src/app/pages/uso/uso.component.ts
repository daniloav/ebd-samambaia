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

      <!-- ===================== D) Uso por funcionalidade ===================== -->
      <h3 style="margin:1.75rem 0 .25rem">Uso por funcionalidade</h3>
      <p class="muted" style="margin:0 0 .9rem;font-size:.82rem">Telas mais abertas e ações registradas nos últimos 30 dias.</p>
      <div class="grid2">
        <div class="card">
          <p class="subt">Telas mais abertas</p>
          @if (u.featuresMaisUsadas.length === 0) {
            <p class="vazio">Sem navegação registrada ainda.</p>
          } @else {
            <div class="bars">
              @for (f of u.featuresMaisUsadas; track f.rotulo) {
                <div class="bar-row">
                  <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ rotuloRecurso(f.rotulo) }}</span>
                  <div class="bar-track"><div class="bar-fill" [style.width.%]="perc(f.quantidade, maxFeature())"></div></div>
                  <span class="bar-val">{{ f.quantidade }}</span>
                </div>
              }
            </div>
          }
        </div>

        <div class="card">
          <p class="subt">Ações registradas (cliques)</p>
          @if (u.acoesNotaveis.length === 0) {
            <p class="vazio">Nenhum clique instrumentado ainda (export, WhatsApp…).</p>
          } @else {
            <div class="bars">
              @for (a of u.acoesNotaveis; track a.rotulo) {
                <div class="bar-row">
                  <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ rotuloRecurso(a.rotulo) }}</span>
                  <div class="bar-track"><div class="bar-fill verde" [style.width.%]="perc(a.quantidade, maxAcao())"></div></div>
                  <span class="bar-val">{{ a.quantidade }}</span>
                </div>
              }
            </div>
          }
        </div>
      </div>

      <!-- ===================== F) Professores & gestão ===================== -->
      <h3 style="margin:1.75rem 0 .25rem">Professores &amp; gestão</h3>
      <p class="muted" style="margin:0 0 .9rem;font-size:.82rem">Atividade de gestão e disciplina da chamada.</p>
      <div class="kpis">
        <div class="kpi verde"><div class="n">{{ u.chamadaPrazo.noPrazo }}</div><div class="l">Chamadas no prazo (no dia da aula)</div></div>
        <div class="kpi laranja"><div class="n">{{ u.chamadaPrazo.atrasadas }}</div><div class="l">Chamadas atrasadas</div></div>
        <div class="kpi roxo"><div class="n">{{ u.chamadaPrazo.pctNoPrazo | number:'1.0-0' }}%</div><div class="l">No prazo</div></div>
        <div class="kpi"><div class="n">{{ turmasCobertas() }}/{{ turmasComAula() }}</div><div class="l">Turmas com a última chamada lançada</div></div>
      </div>

      <div class="grid2">
        <!-- Professores mais ativos (auditoria) -->
        <div class="card">
          <p class="subt">Mais ativos na gestão (30 dias)</p>
          @if (u.professoresMaisAtivos.length === 0) {
            <p class="vazio">Nenhuma ação de gestão registrada.</p>
          } @else {
            <div class="bars">
              @for (pa of u.professoresMaisAtivos; track pa.username) {
                <div class="bar-row">
                  <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ pa.username }} <span class="pill">{{ pa.papel }}</span></span>
                  <div class="bar-track"><div class="bar-fill" [style.width.%]="perc(pa.acessos, maxProf())"></div></div>
                  <span class="bar-val">{{ pa.acessos }}</span>
                </div>
              }
            </div>
            <p class="muted" style="font-size:.75rem;margin-top:.6rem">Nº de ações na auditoria (criar/editar/excluir aluno, aula, prova, usuário).</p>
          }
        </div>

        <!-- Cobertura da chamada por turma (última aula prevista) -->
        <div class="card">
          <p class="subt">Cobertura da chamada por turma</p>
          @if (u.coberturaTurmas.length === 0) {
            <p class="vazio">Nenhuma turma ativa.</p>
          } @else {
            <div class="bars">
              @for (ct of u.coberturaTurmas; track ct.turma) {
                <div style="display:flex;justify-content:space-between;gap:.5rem;align-items:center">
                  <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ ct.turma }}</span>
                  @switch (ct.situacao) {
                    @case ('FEITA') {
                      <span class="pill" style="background:#dcfce7;color:#166534">✓ {{ ct.aulaData | date:'dd/MM' }}</span>
                    }
                    @case ('PENDENTE') {
                      <span class="pill" style="background:#fee2e2;color:#991b1b">pendente ({{ ct.aulaData | date:'dd/MM' }})</span>
                    }
                    @default {
                      <span class="pill" style="background:#e5e7eb;color:#374151">sem aula prevista</span>
                    }
                  }
                </div>
              }
            </div>
          }
          <p class="muted" style="font-size:.75rem;margin-top:.6rem">Olha a última aula prevista de cada turma (não adiada, até hoje): se a chamada dela já foi lançada.</p>
        </div>
      </div>

      <!-- ===================== A) Tempo real (pico + ao vivo) ===================== -->
      <h3 style="margin:1.75rem 0 .25rem">Tempo real</h3>
      <p class="muted" style="margin:0 0 .9rem;font-size:.82rem">Simultaneidade e presença no horário da EBD.</p>
      <div class="kpis">
        <div class="kpi roxo"><div class="n">{{ u.picoHoje }}</div><div class="l">Pico simultâneo hoje (janela 15 min)</div></div>
        <div class="kpi"><div class="n">{{ u.pico30d }}</div><div class="l">Pico simultâneo (30 dias)</div></div>
        <div class="kpi verde"><div class="n">{{ u.aoVivoNaAula }}</div><div class="l">Ativos na EBD de {{ u.aoVivoData | date:'dd/MM' }} (8–12h)</div></div>
      </div>

      <!-- ===================== C) Funil de ativação + coortes ===================== -->
      <h3 style="margin:1.75rem 0 .25rem">Adoção — funil &amp; coortes</h3>
      <div class="grid2">
        <div class="card">
          <p class="subt">Funil de ativação</p>
          <div class="bars">
            @for (et of u.funil; track et.rotulo; let i = $index) {
              <div class="bar-row">
                <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ et.rotulo }}</span>
                <div class="bar-track"><div class="bar-fill" [class.verde]="i === u.funil.length - 1" [style.width.%]="perc(et.quantidade, maxFunil())"></div></div>
                <span class="bar-val">{{ et.quantidade }}</span>
              </div>
            }
          </div>
          <p class="muted" style="font-size:.75rem;margin-top:.6rem">Cadastrado → 1º acesso → trocou a senha padrão → usou uma função.</p>
        </div>

        <div class="card">
          <p class="subt">Coortes por mês de cadastro</p>
          @if (u.coortes.length === 0) {
            <p class="vazio">Sem dados de cadastro.</p>
          } @else {
            <div class="bars">
              <div class="bar-row" style="font-weight:700"><span>Mês</span><span class="muted" style="text-align:center">ativaram / entraram</span><span class="bar-val">ativos</span></div>
              @for (co of u.coortes; track co.rotulo) {
                <div class="bar-row">
                  <span>{{ co.rotulo }}</span>
                  <div class="bar-track"><div class="bar-fill verde" [style.width.%]="perc(co.ativados, co.cadastrados)"></div></div>
                  <span class="bar-val">{{ co.ativados }}/{{ co.cadastrados }}</span>
                </div>
              }
            </div>
            <p class="muted" style="font-size:.75rem;margin-top:.6rem">Barra = % que já acessou; número da direita = entraram no mês.</p>
          }
        </div>
      </div>

      <!-- ===================== E) Engajamento contínuo do aluno ===================== -->
      <h3 style="margin:1.75rem 0 .25rem">Engajamento contínuo (alunos)</h3>
      <div class="kpis">
        <div class="kpi laranja"><div class="n">{{ u.pctForaDoDomingo | number:'1.0-0' }}%</div><div class="l">Da atividade acontece fora do domingo (30d)</div></div>
      </div>
      <div class="card">
        <p class="subt">Semanas seguidas com atividade</p>
        @if (u.streaks.length === 0) {
          <p class="vazio">Nenhum aluno com atividade nas últimas semanas.</p>
        } @else {
          <div class="bars">
            @for (st of u.streaks; track st.username) {
              <div class="bar-row">
                <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ st.username }} <span class="pill">{{ st.papel }}</span></span>
                <div class="bar-track"><div class="bar-fill verde" [style.width.%]="perc(st.semanas, maxStreak())"></div></div>
                <span class="bar-val">{{ st.semanas }} sem</span>
              </div>
            }
          </div>
        }
      </div>

      <!-- ===================== G) Técnico — versão exata + plataforma ===================== -->
      <h3 style="margin:1.75rem 0 .25rem">Técnico — plataforma &amp; versão</h3>
      <p class="muted" style="margin:0 0 .9rem;font-size:.82rem">Base para decidir o upgrade do Angular (exposição a iOS/Safari antigos). PWA instalado × navegador fica p/ um próximo lote.</p>
      <div class="grid2">
        <div class="card">
          <p class="subt">Celular × computador (30 dias)</p>
          @if (u.plataformas.length === 0) {
            <p class="vazio">Sem acessos registrados.</p>
          } @else {
            <div class="bars">
              @for (pl of u.plataformas; track pl.rotulo) {
                <div class="bar-row">
                  <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ pl.rotulo }}</span>
                  <div class="bar-track"><div class="bar-fill" [style.width.%]="perc(pl.quantidade, totalPlataformas())"></div></div>
                  <span class="bar-val">{{ pl.quantidade }}</span>
                </div>
              }
            </div>
          }
        </div>

        <div class="card">
          <p class="subt">Versão de SO / navegador (30 dias)</p>
          @if (u.versoesSistema.length === 0) {
            <p class="vazio">Sem acessos registrados.</p>
          } @else {
            <div class="bars">
              @for (ve of u.versoesSistema; track ve.rotulo) {
                <div class="bar-row">
                  <span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ ve.rotulo }}</span>
                  <div class="bar-track"><div class="bar-fill" [style.width.%]="perc(ve.quantidade, totalVersoes())"></div></div>
                  <span class="bar-val">{{ ve.quantidade }}</span>
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
  maxFeature = computed(() =>
    Math.max(1, ...((this.d()?.featuresMaisUsadas ?? []).map((x) => x.quantidade))));
  maxAcao = computed(() =>
    Math.max(1, ...((this.d()?.acoesNotaveis ?? []).map((x) => x.quantidade))));
  maxProf = computed(() =>
    Math.max(1, ...((this.d()?.professoresMaisAtivos ?? []).map((x) => x.acessos))));
  turmasCobertas = computed(() =>
    (this.d()?.coberturaTurmas ?? []).filter((x) => x.situacao === 'FEITA').length);
  /** Denominador do KPI: só as turmas que têm uma aula prevista para cobrar. */
  turmasComAula = computed(() =>
    (this.d()?.coberturaTurmas ?? []).filter((x) => x.situacao !== 'SEM_AULA').length);
  maxFunil = computed(() =>
    Math.max(1, ...((this.d()?.funil ?? []).map((x) => x.quantidade))));
  maxStreak = computed(() =>
    Math.max(1, ...((this.d()?.streaks ?? []).map((x) => x.semanas))));
  totalPlataformas = computed(() =>
    (this.d()?.plataformas ?? []).reduce((acc, x) => acc + x.quantidade, 0));
  totalVersoes = computed(() =>
    (this.d()?.versoesSistema ?? []).reduce((acc, x) => acc + x.quantidade, 0));

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

  private readonly ROTULOS: Record<string, string> = {
    painel: 'Painel', chamada: 'Chamada', aulas: 'Aulas', alunos: 'Alunos',
    relatorio: 'Relatório de presenças', 'relatorio-visitantes': 'Relatório de visitantes',
    provas: 'Provas', desafios: 'Desafios', boletim: 'Boletim', classes: 'Turmas',
    requisicoes: 'Requisições', conta: 'Minha conta', usuarios: 'Usuários',
    'minha-frequencia': 'Minha frequência', 'meu-boletim': 'Meu boletim',
    'meu-ranking': 'Meu ranking', 'minhas-provas': 'Minhas provas', uso: 'Uso & engajamento',
    'whatsapp-parabenizar': '💬 Parabenizar (WhatsApp)',
    'export-pdf-relatorio': '📄 PDF do relatório', 'export-excel-relatorio': '📊 Excel do relatório',
    'export-pdf-boletim': '📄 PDF do boletim',
  };

  rotuloRecurso(chave: string): string {
    return this.ROTULOS[chave] ?? chave;
  }

  rotuloHora(i: number): string {
    return i % 3 === 0 ? String(i) : '';
  }
}
