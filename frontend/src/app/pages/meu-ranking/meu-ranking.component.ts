import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { MeuRanking, RankingResumoItem } from '../../core/models';

/** Ranking resumido para o aluno: pódio (1º/2º/3º) da turma + a posição dele. */
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
            @if (m.posicao <= 3) { <span style="color:var(--dourado);font-weight:700">🎉 Você está no pódio!</span> }
          </div>
        </div>
      } @else {
        <div class="card no-pod">Você ainda não pontuou nesta turma. Comece participando das aulas! 💪</div>
      }
    }
  `,
})
export class MeuRankingComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  dados = signal<MeuRanking | null>(null);
  carregando = signal(true);

  constructor() {
    this.api.meuRanking().subscribe({
      next: (r) => { this.dados.set(r); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Não foi possível carregar o ranking.'); this.carregando.set(false); },
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
}
