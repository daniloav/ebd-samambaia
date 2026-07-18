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
    .lin { display: flex; align-items: center; gap: .7rem; padding: .55rem 0;
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
  `],
  template: `
    <h2>🏆 Desafios da classe</h2>
    <p class="muted">Destaques calculados a partir da chamada e das notas das provas.</p>

    @if (dados(); as d) {
      <div class="topo">
        <div class="box">Aulas consideradas: <b>{{ d.totalAulas }}</b></div>
        <div class="box">Provas consideradas: <b>{{ d.totalProvas }}</b></div>
      </div>
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
  carregando = signal(true);

  constructor() {
    this.api.rankings().subscribe({
      next: (d) => {
        this.dados.set(d);
        this.categorias.set([
          { titulo: 'Menos faltou', icone: '📅', itens: d.menosFaltou },
          { titulo: 'Mais trouxe a Bíblia', icone: '📖', itens: d.maisTrouxeBiblia },
          { titulo: 'Mais trouxe a revista', icone: '📗', itens: d.maisTrouxeRevista },
          { titulo: 'Mais estudou a lição', icone: '✏️', itens: d.maisEstudouLicao },
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
