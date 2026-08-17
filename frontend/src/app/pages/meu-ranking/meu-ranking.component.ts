import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { MeuRanking, RankingResumoItem, RankingTurmasResponse } from '../../core/models';

/** Ranking resumido para o aluno: pódio (1º/2º/3º) da turma + a posição dele, e a disputa entre turmas. */
@Component({
  selector: 'app-meu-ranking',
  standalone: true,
  styles: [`
    .podio { display: flex; align-items: flex-end; justify-content: center; gap: .8rem; margin: 1.4rem 0 1rem; flex-wrap: wrap; }
    .lugar { flex: 1; max-width: 200px; min-width: 120px; background: var(--superficie); border: 1px solid var(--cinza-borda);
      border-radius: 12px; padding: 1rem .8rem; text-align: center; position: relative; }
    .lugar .medalha { font-size: 2rem; line-height: 1; }
    .lugar .nome { font-weight: 700; color: var(--titulo); margin: .4rem 0 .1rem; font-size: .95rem; }
    .lugar .pts { color: var(--cinza-texto); font-size: .82rem; }
    /* pódio: 1º mais alto */
    .lugar.p1 { padding-top: 1.6rem; border-color: var(--dourado); box-shadow: 0 6px 18px rgba(201,162,75,.22); }
    .lugar.p2 { padding-top: 1.2rem; }
    .eu { outline: 2px solid var(--azul); outline-offset: 2px; }
    .eu-tag { display: inline-block; font-size: .68rem; font-weight: 800; color: #fff; background: var(--azul);
      border-radius: 999px; padding: .1rem .5rem; margin-top: .35rem; }
    .minha { display: flex; align-items: center; gap: 1rem; background: var(--superficie); border: 1px solid var(--cinza-borda);
      border-left: 4px solid var(--azul); border-radius: 12px; padding: 1rem 1.2rem; }
    .minha .pos { font-size: 1.8rem; font-weight: 800; color: var(--azul); font-variant-numeric: tabular-nums; }
    .minha .info b { color: var(--titulo); } .minha .info span { display: block; color: var(--cinza-texto); font-size: .85rem; }
    .no-pod { color: var(--cinza-texto); }

    /* Ranking das turmas */
    .turmas { list-style: none; margin: .6rem 0 0; padding: 0; }
    .turmas li { display: flex; align-items: center; gap: .7rem; padding: .6rem .7rem; border: 1px solid var(--cinza-borda);
      border-radius: 10px; margin-bottom: .5rem; background: var(--superficie); }
    .turmas li.minha-turma { border-color: var(--azul); border-left: 4px solid var(--azul); background: #f5f9ff; }
    .turmas .t-pos { width: 34px; text-align: center; font-weight: 800; font-size: 1.15rem; }
    .turmas .t-nome { flex: 1; font-weight: 700; color: var(--titulo); }
    .turmas .t-nome small { display: block; color: var(--cinza-texto); font-size: .78rem; font-weight: 500; }
    .turmas .t-val { font-weight: 800; color: var(--alerta); text-align: right; }
    .turmas .t-val small { display: block; font-size: .68rem; color: var(--cinza-texto); font-weight: 600; }
    .sua-tag { display: inline-block; font-size: .66rem; font-weight: 800; color: #fff; background: var(--azul);
      border-radius: 999px; padding: .05rem .45rem; margin-left: .4rem; }
  `],
  template: `
    <h2>🏆 Ranking da turma</h2>
    <p class="muted">Os três primeiros da classificação geral{{ dados()?.turmaNome ? ' — ' + dados()!.turmaNome : '' }} e a sua posição.</p>

    @if (carregando()) {
      <div class="card muted">Carregando...</div>
    } @else if (!dados() || dados()!.totalParticipantes === 0) {
      <div class="card no-pod text-center">Ainda não há ranking nesta turma. Participe das aulas para pontuar! 🙌</div>
    } @else {
      <div class="card">
        <div class="podio">
          @for (p of ordemPodio(); track p.alunoId) {
            <div class="lugar" [class.p1]="p.posicao === 1" [class.p2]="p.posicao === 2" [class.eu]="p.eu">
              <div class="medalha">{{ medalha(p.posicao) }}</div>
              <div class="nome">{{ p.nome }}</div>
              <div class="pts">{{ p.valor }} pts</div>
              @if (p.eu) { <div class="eu-tag">Você</div> }
            </div>
          }
        </div>
      </div>

      <h3 style="margin-bottom:.5rem">Sua posição</h3>
      @if (dados()!.minhaPosicao; as m) {
        <div class="minha">
          <div class="pos">{{ m.posicao }}º</div>
          <div class="info">
            <b>{{ m.eu ? 'Você' : m.nome }} · {{ m.valor }} pts</b>
            <span>{{ m.detalhe }}</span>
            @if (m.posicao <= 3) { <span style="color:var(--alerta);font-weight:700">🎉 Você está no pódio!</span> }
          </div>
        </div>
      } @else {
        <div class="card no-pod">Você ainda não pontuou nesta turma. Comece participando das aulas! 💪</div>
      }
    }

    @if (turmas(); as t) {
      @if (t.turmas.length > 1) {
        <h3 style="margin:1.6rem 0 .3rem">🏫 Ranking das turmas</h3>
        <p class="muted">Disputa entre as turmas pela <b>média de pontos por aluno</b> — sua turma está destacada.</p>
        <ul class="turmas">
          @for (item of t.turmas; track item.classeId) {
            <li [class.minha-turma]="item.classeId === t.minhaClasseId">
              <span class="t-pos">{{ medalha(item.posicao) }}</span>
              <span class="t-nome">{{ item.turmaNome }}
                @if (item.classeId === t.minhaClasseId) { <span class="sua-tag">Sua turma</span> }
                <small>{{ item.detalhe }}</small>
              </span>
              <span class="t-val">{{ formatar(item.valor) }}<small>méd/aluno</small></span>
            </li>
          }
        </ul>
      }
    }
  `,
})
export class MeuRankingComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  dados = signal<MeuRanking | null>(null);
  carregando = signal(true);
  turmas = signal<RankingTurmasResponse | null>(null);

  constructor() {
    this.api.meuRanking().subscribe({
      next: (r) => { this.dados.set(r); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Não foi possível carregar o ranking.'); this.carregando.set(false); },
    });
    // Ranking das turmas (silencioso: se falhar, apenas não exibe a seção).
    this.api.meuRankingTurmas().subscribe({
      next: (t) => this.turmas.set(t),
      error: () => this.turmas.set(null),
    });
  }

  /** Ordena o pódio como 2º · 1º · 3º (visual de pódio). */
  ordemPodio(): RankingResumoItem[] {
    const p = this.dados()?.podio ?? [];
    const byPos = (n: number) => p.find((x) => x.posicao === n);
    return [byPos(2), byPos(1), byPos(3)].filter((x): x is RankingResumoItem => !!x);
  }

  medalha(pos: number): string {
    return pos === 1 ? '🥇' : pos === 2 ? '🥈' : pos === 3 ? '🥉' : pos + 'º';
  }

  formatar(valor: number): string {
    return Number.isInteger(valor) ? String(valor) : valor.toFixed(2);
  }
}
