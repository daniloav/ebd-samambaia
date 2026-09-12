import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { ToastService } from '../../core/toast.service';
import { QuizParaResponder, ResultadoProva } from '../../core/models';

/**
 * Responder um quiz e ver o resultado (nota + gabarito). Quiz online: 1 tentativa. Recuperação:
 * até 3, com questões embaralhadas a cada uma (pelo backend) e "Tentar de novo" no resultado.
 */
@Component({
  selector: 'app-responder-prova',
  standalone: true,
  imports: [RouterLink, DatePipe],
  styles: [`
    .q { border: 1px solid var(--cinza-borda); border-radius: 10px; padding: 1rem 1.1rem; margin-bottom: 1rem; }
    .q .enun { font-weight: 600; color: var(--titulo); margin-bottom: .2rem; }
    .q .pts { font-size: .8rem; color: var(--cinza-texto); margin-bottom: .7rem; }
    .opt { display: flex; align-items: center; gap: .6rem; padding: .55rem .7rem; border: 1px solid var(--cinza-borda);
      border-radius: 8px; margin-bottom: .5rem; cursor: pointer; }
    .opt:hover { border-color: var(--primaria); }
    .opt.sel { border-color: var(--primaria); background: color-mix(in srgb, var(--primaria) 10%, transparent); }
    .opt.correta { border-color: #16a34a; background: #dcfce7; }
    .opt.errada  { border-color: #dc2626; background: #fee2e2; }
    .opt input { margin: 0; }
    .rodape { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; margin-top: 1rem; }
    .placar { text-align: center; padding: 1.2rem; border-radius: 12px; background: var(--superficie-2, #f8fafc); margin-bottom: 1.2rem; }
    .placar .n { font-size: 2.2rem; font-weight: 800; color: var(--titulo); }
    .tag { font-size: .8rem; font-weight: 700; padding: .15rem .55rem; border-radius: 999px; margin-left: .5rem; }
    .tag-ok { background: #dcfce7; color: #166534; }
    .tag-no { background: #fee2e2; color: #991b1b; }
  `],
  template: `
    <a routerLink="/minhas-provas" class="muted">← Voltar para minhas provas</a>

    @if (carregando()) {
      <div class="card muted" style="margin-top:.5rem">Carregando...</div>
    } @else {
      @if (resultado(); as r) {
        <h2 style="margin-top:.5rem">Resultado — {{ titulo() }}</h2>
        <div class="card">
          <div class="placar">
            <div class="n">{{ r.nota }} <span style="font-size:1.1rem;color:var(--cinza-texto)">/ {{ r.notaMaxima }}</span></div>
            <div class="muted">Você acertou {{ r.acertos }} de {{ r.total }} questão(ões).</div>
            @if (r.tipo === 'RECUPERACAO') {
              <div class="muted" style="margin-top:.4rem">
                Tentativa {{ r.tentativa }} de {{ r.tentativasMax }} · melhor nota <b>{{ r.melhorNota }}</b>
                = <b>{{ r.presencaEquivalente }}</b> presença na aula
              </div>
              @if (r.podeTentarDeNovo) {
                <button class="btn" style="margin-top:.8rem" (click)="novaTentativa()">
                  Tentar de novo (resta{{ restantes(r) > 1 ? 'm' : '' }} {{ restantes(r) }})
                </button>
              }
            }
          </div>
          @for (q of r.questoes; track q.questaoId; let i = $index) {
            <div class="q">
              <div class="enun">{{ i + 1 }}. {{ q.enunciado }}
                <span class="tag" [class]="q.acertou ? 'tag-ok' : 'tag-no'">{{ q.acertou ? 'acertou' : 'errou' }}</span>
              </div>
              <div class="pts">Vale {{ q.pontos }} ponto(s)</div>
              @for (a of q.alternativas; track a.id) {
                <div class="opt" [class.correta]="a.id === q.corretaId"
                     [class.errada]="a.id === q.escolhidaId && a.id !== q.corretaId">
                  <span>{{ a.texto }}</span>
                  @if (a.id === q.corretaId) { <span class="muted" style="margin-left:auto">✔ correta</span> }
                  @else if (a.id === q.escolhidaId) { <span class="muted" style="margin-left:auto">sua resposta</span> }
                </div>
              }
            </div>
          }
        </div>
      } @else {
        @if (quiz(); as quiz) {
          <h2 style="margin-top:.5rem">{{ quiz.titulo }}</h2>
          @if (quiz.tipo === 'RECUPERACAO') {
            <p class="muted">
              Recuperação da aula de {{ quiz.aulaData | date:'dd/MM/yyyy' }}{{ quiz.aulaTema ? ' — ' + quiz.aulaTema : '' }}.
              <b>Tentativa {{ quiz.tentativa }} de {{ quiz.tentativasMax }}</b> · {{ quiz.questoes.length }} questão(ões).
              Acertando tudo você ganha 1 presença; senão, o proporcional. Vale a melhor tentativa.
            </p>
          } @else {
            <p class="muted">Vale {{ quiz.notaMaxima }} pontos · {{ quiz.questoes.length }} questão(ões). Você tem 1 tentativa.</p>
          }
          <div class="card">
            @for (q of quiz.questoes; track q.id; let i = $index) {
              <div class="q">
                <div class="enun">{{ i + 1 }}. {{ q.enunciado }}</div>
                <div class="pts">Vale {{ q.pontos }} ponto(s)</div>
                @for (a of q.alternativas; track a.id) {
                  <label class="opt" [class.sel]="escolhas[q.id] === a.id">
                    <input type="radio" [name]="'q-' + q.id" [checked]="escolhas[q.id] === a.id"
                           (change)="escolher(q.id, a.id)" />
                    <span>{{ a.texto }}</span>
                  </label>
                }
              </div>
            }
            <div class="rodape">
              <span class="muted">{{ respondidas() }} de {{ quiz.questoes.length }} respondidas</span>
              <button class="btn btn-verde" (click)="enviar()" [disabled]="enviando()">
                {{ enviando() ? \'Enviando...\' : \'Enviar respostas\' }}
              </button>
            </div>
          </div>
        } @else {
          <div class="card muted" style="margin-top:.5rem">Prova indisponível.</div>
        }
      }
    }
  `,
})
export class ResponderProvaComponent {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private confirm = inject(ConfirmService);
  private toast = inject(ToastService);

  provaId!: number;
  carregando = signal(true);
  enviando = signal(false);
  quiz = signal<QuizParaResponder | null>(null);
  resultado = signal<ResultadoProva | null>(null);
  titulo = signal('');
  escolhas: Record<number, number> = {};

  respondidas(): number { return Object.keys(this.escolhas).length; }

  constructor() {
    this.provaId = Number(this.route.snapshot.paramMap.get('id'));
    if (this.route.snapshot.queryParamMap.get('ver') === 'resultado') {
      this.carregarResultado();
    } else {
      this.abrirParaResponder();
    }
  }

  /** Tenta abrir para responder; se não há tentativa disponível, mostra o resultado salvo. */
  private abrirParaResponder(): void {
    this.carregando.set(true);
    this.api.obterProvaParaResponder(this.provaId).subscribe({
      next: (q) => { this.quiz.set(q); this.titulo.set(q.titulo); this.carregando.set(false); },
      error: () => this.carregarResultado(),
    });
  }

  private carregarResultado(): void {
    this.carregando.set(true);
    this.api.obterResultadoProva(this.provaId).subscribe({
      next: (r) => { this.resultado.set(r); this.titulo.set(r.titulo); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Prova indisponível.'); this.carregando.set(false); },
    });
  }

  restantes(r: ResultadoProva): number { return r.tentativasMax - r.tentativa; }

  /** Nova tentativa da recuperação: o backend devolve as questões numa ordem nova. */
  novaTentativa(): void {
    this.escolhas = {};
    this.resultado.set(null);
    this.quiz.set(null);
    this.abrirParaResponder();
    window.scrollTo({ top: 0 });
  }

  escolher(questaoId: number, alternativaId: number): void {
    this.escolhas[questaoId] = alternativaId;
  }

  async enviar(): Promise<void> {
    const q = this.quiz();
    if (!q) { return; }
    const faltam = q.questoes.length - this.respondidas();
    const restam = q.tentativasMax - q.tentativa;
    const depois = restam > 0
      ? `Esta é a tentativa ${q.tentativa} de ${q.tentativasMax}; ainda restará${restam > 1 ? 'ão' : ''} ${restam}.`
      : 'Você não poderá refazer esta prova.';
    const msg = faltam > 0
      ? `Você deixou ${faltam} questão(ões) sem resposta. Enviar mesmo assim? ${depois}`
      : `Enviar suas respostas? ${depois}`;
    if (!(await this.confirm.pedir({ mensagem: msg, titulo: 'Enviar respostas', confirmar: 'Enviar' }))) { return; }

    const respostas = q.questoes.map((qq) => ({ questaoId: qq.id, alternativaId: this.escolhas[qq.id] ?? null }));
    this.enviando.set(true);
    this.api.submeterProva(this.provaId, respostas).subscribe({
      next: (r) => { this.resultado.set(r); this.enviando.set(false); this.toast.sucesso('Respostas enviadas!'); window.scrollTo({ top: 0 }); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Não foi possível enviar.'); this.enviando.set(false); },
    });
  }
}
