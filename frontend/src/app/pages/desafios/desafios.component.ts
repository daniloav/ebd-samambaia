import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { DesafiosResponse, RankingItem } from '../../core/models';

interface Categoria {
  titulo: string;
  icone: string;
  itens: RankingItem[];
}

@Component({
  selector: 'app-desafios',
  standalone: true,
  styles: [`
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1.25rem; }
    .cat { background: #fff; border-radius: var(--raio); box-shadow: var(--sombra);
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
    .topo .box { background: #fff; border-radius: 8px; box-shadow: var(--sombra); padding: .7rem 1.1rem; }
    .topo b { color: var(--azul); }

    /* Classificação geral (destaque) */
    .geral-card { background: #fff; border-radius: var(--raio); box-shadow: var(--sombra-md);
                  padding: 1.4rem 1.5rem; margin-bottom: 1.5rem; border-top: 4px solid var(--dourado); }
    .geral-card h3 { font-size: 1.25rem; margin-bottom: .1rem; display: flex; gap: .5rem; align-items: center; }
    .muted-sm { color: var(--cinza-texto); font-size: .82rem; margin: 0 0 .6rem; }
    .lin-geral .valor { color: var(--dourado); }
    .lin-geral.top1 { background: #fffbeb; border-radius: 8px; }
    .valor small { font-size: .7rem; color: var(--cinza-texto); font-weight: 600; }
    .subtitulo { margin: 0 0 1rem; color: var(--cinza-texto); font-size: .9rem; }
  `],
  template: `
    <h2>🏆 Desafios da classe</h2>
    <p class="subtitulo">Destaques calculados a partir da chamada e das notas das provas.</p>

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
  `,
})
export class DesafiosComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  dados = signal<DesafiosResponse | null>(null);
  categorias = signal<Categoria[]>([]);
  geral = signal<RankingItem[]>([]);
  carregando = signal(true);

  constructor() {
    this.api.rankings().subscribe({
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

  medalha(pos: number): string {
    return pos === 1 ? '🥇' : pos === 2 ? '🥈' : pos === 3 ? '🥉' : String(pos) + 'º';
  }

  formatar(valor: number): string {
    return Number.isInteger(valor) ? String(valor) : valor.toFixed(2);
  }
}
