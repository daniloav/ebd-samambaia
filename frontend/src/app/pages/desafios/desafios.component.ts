import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { DesafiosResponse, RankingItem, RankingTurmasResponse } from '../../core/models';

interface Categoria {
  titulo: string;
  icone: string;
  itens: RankingItem[];
}

@Component({
  selector: 'app-desafios',
  standalone: true,
  imports: [FormsModule],
  styles: [`
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1.25rem; }
    .filtro-periodo { display: flex; align-items: center; gap: .6rem; flex-wrap: wrap; margin: 0 0 1rem; }
    .filtro-periodo label { font-weight: 600; color: var(--cinza-texto); }
    .abas { display: inline-flex; gap: .25rem; background: var(--cinza-borda); padding: .25rem;
            border-radius: 10px; margin-bottom: 1rem; }
    .abas button { border: none; background: transparent; padding: .5rem 1.1rem; border-radius: 8px;
                   font-weight: 700; cursor: pointer; color: var(--cinza-texto); font-size: .92rem; }
    .abas button.ativa { background: var(--superficie); color: var(--azul); box-shadow: var(--sombra); }
    .cat { background: var(--superficie); border-radius: var(--raio); box-shadow: var(--sombra);
           padding: 1.25rem; border-top: 3px solid var(--dourado); }
    .cat h3 { display: flex; align-items: center; gap: .5rem; font-size: 1.05rem; }
    .lista { list-style: none; margin: .75rem 0 0; padding: 0; }
    .lin { display: flex; align-items: center; gap: .7rem; padding: .55rem .4rem;
           border-bottom: 1px solid var(--cinza-borda); }
    .lin:last-child { border-bottom: none; }
    .pos { width: 30px; text-align: center; font-weight: 800; font-size: 1.1rem; }
    .pos.medalha { font-size: 1.3rem; }
    .nome { flex: 1; font-weight: 600; }
    .detalhe { color: var(--cinza-texto); font-size: .8rem; }
    .valor { font-weight: 800; color: var(--azul); font-size: 1.1rem; }
    .vazio { color: var(--cinza-texto); font-size: .88rem; padding: .5rem 0; }
    .topo { display: flex; gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .topo .box { background: var(--superficie); border-radius: 8px; box-shadow: var(--sombra); padding: .7rem 1.1rem; }
    .topo b { color: var(--azul); }

    /* Classificação geral (destaque) */
    .geral-card { background: var(--superficie); border-radius: var(--raio); box-shadow: var(--sombra-md);
                  padding: 1.4rem 1.5rem; margin-bottom: 1.5rem; border-top: 4px solid var(--dourado); }
    .geral-card h3 { font-size: 1.25rem; margin-bottom: .1rem; display: flex; gap: .5rem; align-items: center; }
    .muted-sm { color: var(--cinza-texto); font-size: .82rem; margin: 0 0 .6rem; }
    .lin-geral .valor { color: var(--alerta); }
    .lin-geral.top1 { background: rgba(201, 162, 75, .14); border-radius: 8px; }
    .valor small { font-size: .7rem; color: var(--cinza-texto); font-weight: 600; }
    .subtitulo { margin: 0 0 1rem; color: var(--cinza-texto); font-size: .9rem; }
  `],
  template: `
    <h2>🏆 Desafios</h2>

    <div class="abas">
      <button [class.ativa]="aba() === 'individual'" (click)="trocarAba('individual')">👤 Individual</button>
      <button [class.ativa]="aba() === 'turma'" (click)="trocarAba('turma')">🏫 Por turma</button>
    </div>

    <div class="filtro-periodo">
      <label>Período:</label>
      <select aria-label="Período (trimestre)" [(ngModel)]="periodoTri" (ngModelChange)="onPeriodo()">
        <option [ngValue]="null">Todo o período</option>
        <option [ngValue]="1">1º trimestre (Jan-Mar)</option>
        <option [ngValue]="2">2º trimestre (Abr-Jun)</option>
        <option [ngValue]="3">3º trimestre (Jul-Set)</option>
        <option [ngValue]="4">4º trimestre (Out-Dez)</option>
      </select>
      @if (periodoTri !== null) {
        <input type="number" min="2000" max="2100" [(ngModel)]="ano" (ngModelChange)="onPeriodo()"
               style="width:90px" aria-label="Ano" />
      }
    </div>

    @if (aba() === 'individual') {
      <p class="subtitulo">Destaques dos alunos, calculados a partir da chamada e das notas das provas.</p>
      @if (dados(); as d) {
        <div class="topo">
          <div class="box">Aulas consideradas: <b>{{ d.totalAulas }}</b></div>
          <div class="box">Provas consideradas: <b>{{ d.totalProvas }}</b></div>
        </div>

        @if (geral().length) {
          <div class="geral-card">
            <h3><span>🥇</span> Classificação geral</h3>
            <p class="muted-sm">Soma de todos os quesitos: 1 ponto por presença, Bíblia, revista e lição,
              2 pontos por visitante, mais os pontos das notas.</p>
            <ul class="lista">
              @for (i of geral(); track i.alunoId) {
                <li class="lin lin-geral" [class.top1]="i.posicao === 1">
                  <span class="pos medalha">{{ medalha(i.posicao) }}</span>
                  <span class="nome">{{ i.nome }}<br><span class="detalhe">{{ i.detalhe }}</span></span>
                  <span class="valor">{{ formatar(i.valor) }} <small>pts</small></span>
                </li>
              }
            </ul>
          </div>
        }

        <div class="grid">
          @for (c of categorias(); track c.titulo) {
            <div class="cat">
              <h3><span>{{ c.icone }}</span> {{ c.titulo }}</h3>
              @if (c.itens.length === 0) {
                <p class="vazio">Sem dados suficientes ainda.</p>
              } @else {
                <ul class="lista">
                  @for (i of c.itens; track i.alunoId) {
                    <li class="lin">
                      <span class="pos" [class.medalha]="i.posicao <= 3">{{ medalha(i.posicao) }}</span>
                      <span class="nome">{{ i.nome }}<br><span class="detalhe">{{ i.detalhe }}</span></span>
                      <span class="valor">{{ formatar(i.valor) }}</span>
                    </li>
                  }
                </ul>
              }
            </div>
          }
        </div>
      } @else if (carregando()) {
        <div class="spinner-wrap muted">Carregando rankings...</div>
      }
    } @else {
      <p class="subtitulo">Disputa sadia entre as turmas: cada turma pontua pela <b>média de pontos por
        aluno</b>, para que turmas de tamanhos diferentes compitam de forma justa.</p>
      @if (dadosTurma(); as d) {
        <div class="topo">
          <div class="box">Aulas consideradas: <b>{{ d.totalAulas }}</b></div>
          <div class="box">Provas consideradas: <b>{{ d.totalProvas }}</b></div>
        </div>

        @if (d.turmas.length) {
          <div class="geral-card">
            <h3><span>🏫</span> Ranking das turmas</h3>
            <p class="muted-sm">Média de pontos por aluno = total de pontos da turma ÷ nº de alunos ativos.</p>
            <ul class="lista">
              @for (t of d.turmas; track t.classeId) {
                <li class="lin lin-geral" [class.top1]="t.posicao === 1">
                  <span class="pos medalha">{{ medalha(t.posicao) }}</span>
                  <span class="nome">{{ t.turmaNome }}<br><span class="detalhe">{{ t.detalhe }}</span></span>
                  <span class="valor">{{ formatar(t.valor) }} <small>méd/aluno</small></span>
                </li>
              }
            </ul>
          </div>
        } @else {
          <p class="vazio">Ainda não há dados suficientes para comparar as turmas.</p>
        }
      } @else if (carregandoTurma()) {
        <div class="spinner-wrap muted">Carregando ranking das turmas...</div>
      }
    }
  `,
})
export class DesafiosComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private classeCtx = inject(ClasseContextService);

  aba = signal<'individual' | 'turma'>('individual');

  // Individual
  dados = signal<DesafiosResponse | null>(null);
  categorias = signal<Categoria[]>([]);
  geral = signal<RankingItem[]>([]);
  carregando = signal(true);

  // Por turma
  dadosTurma = signal<RankingTurmasResponse | null>(null);
  carregandoTurma = signal(false);

  periodoTri: number | null = null;
  ano = new Date().getFullYear();

  constructor() {
    // O ranking individual reage à turma selecionada no seletor global; o de turmas não.
    effect(() => { this.classeCtx.selecionadaId(); if (this.aba() === 'individual') { this.carregar(); } },
      { allowSignalWrites: true });
  }

  trocarAba(a: 'individual' | 'turma'): void {
    this.aba.set(a);
    if (a === 'turma') { this.carregarTurmas(); } else { this.carregar(); }
  }

  onPeriodo(): void {
    if (this.aba() === 'turma') { this.carregarTurmas(); } else { this.carregar(); }
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.rankings(this.classeCtx.selecionadaId(), this.periodoTri != null ? this.ano : null, this.periodoTri).subscribe({
      next: (d) => {
        this.dados.set(d);
        this.geral.set(d.classificacaoGeral);
        this.categorias.set([
          { titulo: 'Menos faltou', icone: '📅', itens: d.menosFaltou },
          { titulo: 'Mais trouxe a Bíblia', icone: '📖', itens: d.maisTrouxeBiblia },
          { titulo: 'Mais trouxe a revista', icone: '📗', itens: d.maisTrouxeRevista },
          { titulo: 'Mais estudou a lição', icone: '✏️', itens: d.maisEstudouLicao },
          { titulo: 'Mais trouxe visitantes', icone: '🤝', itens: d.maisTrouxeVisitante },
          { titulo: 'Melhores notas nas provas', icone: '🎓', itens: d.melhoresNotas },
        ]);
        this.carregando.set(false);
      },
      error: () => { this.toast.erro('Falha ao carregar rankings.'); this.carregando.set(false); },
    });
  }

  carregarTurmas(): void {
    this.carregandoTurma.set(true);
    this.dadosTurma.set(null);
    this.api.rankingTurmas(this.periodoTri != null ? this.ano : null, this.periodoTri).subscribe({
      next: (d) => { this.dadosTurma.set(d); this.carregandoTurma.set(false); },
      error: () => { this.toast.erro('Falha ao carregar o ranking das turmas.'); this.carregandoTurma.set(false); },
    });
  }

  medalha(pos: number): string {
    return pos === 1 ? '🥇' : pos === 2 ? '🥈' : pos === 3 ? '🥉' : String(pos) + 'º';
  }

  formatar(valor: number): string {
    return Number.isInteger(valor) ? String(valor) : valor.toFixed(2);
  }
}
