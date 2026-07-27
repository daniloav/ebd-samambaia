import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { Prova, QuizQuestaoEdit } from '../../core/models';

@Component({
  selector: 'app-quiz-editor',
  standalone: true,
  imports: [FormsModule, RouterLink],
  styles: [`
    .q { border: 1px solid var(--cinza-borda); border-radius: 10px; padding: 1rem 1.1rem; margin-bottom: 1rem; }
    .q-topo { display: flex; gap: .6rem; align-items: center; flex-wrap: wrap; margin-bottom: .6rem; }
    .q-topo .num { font-weight: 800; color: var(--titulo); }
    .q-topo select, .q-topo input[type=number] { padding: .35rem .5rem; }
    .q-topo input[type=number] { width: 80px; }
    .alt { display: flex; align-items: center; gap: .6rem; margin-bottom: .5rem; }
    .alt input[type=text] { flex: 1; }
    .alt .corr { display: flex; align-items: center; gap: .3rem; font-size: .82rem; color: var(--cinza-texto); white-space: nowrap; }
    .rodape { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; margin-top: 1rem; }
    .total { font-weight: 700; color: var(--titulo); }
  `],
  template: `
    <a routerLink="/provas" class="muted">← Voltar para provas</a>
    <h2 style="margin-top:.5rem">Montar quiz{{ prova() ? ' — ' + prova()?.titulo : '' }}</h2>
    <p class="muted">Cada questão tem 1 alternativa correta. A nota máxima será a soma dos pontos.</p>

    @if (carregando()) {
      <div class="spinner-wrap muted">Carregando...</div>
    } @else {
      <div class="card">
        @for (q of questoes; track q; let qi = $index) {
          <div class="q">
            <div class="q-topo">
              <span class="num">{{ qi + 1 }}.</span>
              <select [ngModel]="q.tipo" (ngModelChange)="mudarTipo(qi, $event)">
                <option value="MULTIPLA">Múltipla escolha</option>
                <option value="VF">Verdadeiro / Falso</option>
              </select>
              <label style="margin:0;font-size:.8rem">Pontos</label>
              <input type="number" min="0.5" step="0.5" [(ngModel)]="q.pontos" />
              <button class="btn btn-perigo btn-sm" style="margin-left:auto" (click)="removerQuestao(qi)">Remover</button>
            </div>
            <div class="form-group">
              <textarea rows="2" [(ngModel)]="q.enunciado" maxlength="1000" placeholder="Enunciado da questão..."></textarea>
            </div>
            @for (a of q.alternativas; track a; let ai = $index) {
              <div class="alt">
                <input type="text" [(ngModel)]="a.texto" maxlength="500" [readonly]="q.tipo === 'VF'"
                       placeholder="Texto da alternativa" />
                <label class="corr">
                  <input type="radio" [name]="'correta-' + qi" [checked]="a.correta" (change)="marcarCorreta(qi, ai)" />
                  correta
                </label>
                @if (q.tipo === 'MULTIPLA' && q.alternativas.length > 2) {
                  <button class="btn btn-outline btn-sm" (click)="removerAlternativa(qi, ai)">×</button>
                }
              </div>
            }
            @if (q.tipo === 'MULTIPLA') {
              <button class="btn btn-outline btn-sm" (click)="adicionarAlternativa(qi)">+ Alternativa</button>
            }
          </div>
        }

        <div style="display:flex;gap:.6rem;flex-wrap:wrap">
          <button class="btn btn-outline" (click)="adicionarQuestao('MULTIPLA')">+ Múltipla escolha</button>
          <button class="btn btn-outline" (click)="adicionarQuestao('VF')">+ Verdadeiro/Falso</button>
        </div>

        <div class="rodape">
          <span class="total">{{ questoes.length }} questão(ões) · {{ totalPontos() }} pontos</span>
          <button class="btn btn-verde" (click)="salvar()" [disabled]="salvando() || questoes.length === 0">
            {{ salvando() ? 'Salvando...' : '💾 Salvar quiz' }}
          </button>
        </div>
      </div>
    }
  `,
})
export class QuizEditorComponent {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private toast = inject(ToastService);

  provaId!: number;
  prova = signal<Prova | null>(null);
  carregando = signal(true);
  salvando = signal(false);
  questoes: QuizQuestaoEdit[] = [];

  totalPontos = () => this.questoes.reduce((s, q) => s + (Number(q.pontos) || 0), 0);

  constructor() {
    this.provaId = Number(this.route.snapshot.paramMap.get('id'));
    this.api.listarProvas().subscribe({
      next: (ps) => this.prova.set(ps.find((x) => x.id === this.provaId) ?? null),
      error: () => {},
    });
    this.api.obterQuestoesProva(this.provaId).subscribe({
      next: (qs) => { this.questoes = qs.length ? qs : []; this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar as questões.'); this.carregando.set(false); },
    });
  }

  adicionarQuestao(tipo: 'MULTIPLA' | 'VF'): void {
    this.questoes = [...this.questoes, this.novaQuestao(tipo)];
  }

  private novaQuestao(tipo: 'MULTIPLA' | 'VF'): QuizQuestaoEdit {
    if (tipo === 'VF') {
      return { enunciado: '', tipo: 'VF', pontos: 1,
        alternativas: [{ texto: 'Verdadeiro', correta: true }, { texto: 'Falso', correta: false }] };
    }
    return { enunciado: '', tipo: 'MULTIPLA', pontos: 1,
      alternativas: [{ texto: '', correta: true }, { texto: '', correta: false }] };
  }

  removerQuestao(qi: number): void { this.questoes = this.questoes.filter((_, i) => i !== qi); }

  mudarTipo(qi: number, tipo: 'MULTIPLA' | 'VF'): void {
    const nova = this.novaQuestao(tipo);
    nova.enunciado = this.questoes[qi].enunciado;
    nova.pontos = this.questoes[qi].pontos;
    this.questoes = this.questoes.map((q, i) => (i === qi ? nova : q));
  }

  adicionarAlternativa(qi: number): void {
    this.questoes[qi].alternativas.push({ texto: '', correta: false });
    this.questoes = [...this.questoes];
  }
  removerAlternativa(qi: number, ai: number): void {
    const q = this.questoes[qi];
    const eraCorreta = q.alternativas[ai].correta;
    q.alternativas = q.alternativas.filter((_, i) => i !== ai);
    if (eraCorreta && q.alternativas.length) { q.alternativas[0].correta = true; }
    this.questoes = [...this.questoes];
  }
  marcarCorreta(qi: number, ai: number): void {
    this.questoes[qi].alternativas.forEach((a, i) => (a.correta = i === ai));
    this.questoes = [...this.questoes];
  }

  salvar(): void {
    for (let i = 0; i < this.questoes.length; i++) {
      const q = this.questoes[i];
      if (!q.enunciado?.trim()) { this.toast.erro(`Questão ${i + 1}: informe o enunciado.`); return; }
      if (q.alternativas.some((a) => !a.texto?.trim())) { this.toast.erro(`Questão ${i + 1}: há alternativa sem texto.`); return; }
      if (q.alternativas.filter((a) => a.correta).length !== 1) { this.toast.erro(`Questão ${i + 1}: marque exatamente 1 correta.`); return; }
    }
    this.salvando.set(true);
    this.api.salvarQuestoesProva(this.provaId, this.questoes).subscribe({
      next: () => { this.toast.sucesso('Quiz salvo!'); this.salvando.set(false); this.router.navigate(['/provas']); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao salvar o quiz.'); this.salvando.set(false); },
    });
  }
}
