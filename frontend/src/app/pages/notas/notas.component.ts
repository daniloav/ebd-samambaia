import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../core/confirm.service';
import { NotasProvaResponse, NotaItem } from '../../core/models';

@Component({
  selector: 'app-notas',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  styles: [`
    .cab { display: flex; gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .cab .box { background: #f7fafc; border-radius: 8px; padding: .6rem 1rem; font-size: .9rem; }
    .cab b { color: var(--azul); }
    input.nota { width: 90px; text-align: center; }
    .invalida { border-color: var(--vermelho) !important; }
  `],
  template: `
    <a routerLink="/provas" class="muted">← Voltar para provas</a>
    @if (dados(); as d) {
      <h2 style="margin-top:.5rem">Notas — {{ d.titulo }}</h2>
      <div class="card">
        <div class="cab">
          <div class="box">Data: <b>{{ d.data | date:'dd/MM/yyyy' }}</b></div>
          <div class="box">Nota máxima: <b>{{ d.notaMaxima }}</b></div>
          <div class="box">Alunos: <b>{{ itens().length }}</b></div>
        </div>

        @if (itens().length === 0) {
          <p class="muted text-center">Nenhum aluno ativo para lançar notas.</p>
        } @else {
          <div class="tabela-scroll">
            <table class="tabela">
              <thead><tr><th>Aluno</th><th style="width:140px">Nota (0 a {{ d.notaMaxima }})</th></tr></thead>
              <tbody>
                @for (i of itens(); track i.alunoId) {
                  <tr>
                    <td>{{ i.alunoNome }}</td>
                    <td>
                      <input class="nota" type="number" min="0" [max]="d.notaMaxima" step="0.1"
                             [class.invalida]="invalida(i)" [(ngModel)]="i.nota"
                             placeholder="—" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <p class="muted mt">Deixe em branco para não lançar nota do aluno.</p>
          <div class="mt" style="display:flex;gap:.6rem;flex-wrap:wrap">
            <button class="btn btn-verde" (click)="salvar()" [disabled]="salvando()">
              {{ salvando() ? 'Salvando...' : '💾 Salvar notas' }}
            </button>
            <button class="btn btn-outline" (click)="notificar()" [disabled]="salvando() || notificando()">
              {{ notificando() ? 'Enviando...' : '✉️ Lançar e notificar alunos' }}
            </button>
          </div>
          <p class="muted mt">"Lançar e notificar" envia a cada aluno (com e-mail e que aceitou receber avisos) o seu desempenho. Salve as notas antes.</p>
        }
      </div>
    } @else if (carregando()) {
      <div class="spinner-wrap muted">Carregando...</div>
    }
  `,
})
export class NotasComponent {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  provaId!: number;
  dados = signal<NotasProvaResponse | null>(null);
  itens = signal<NotaItem[]>([]);
  carregando = signal(true);
  salvando = signal(false);
  notificando = signal(false);

  constructor() {
    this.provaId = Number(this.route.snapshot.paramMap.get('id'));
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.obterNotas(this.provaId).subscribe({
      next: (r) => {
        this.dados.set(r);
        this.itens.set(r.itens.map((i) => ({ ...i })));
        this.carregando.set(false);
      },
      error: () => { this.toast.erro('Falha ao carregar notas.'); this.carregando.set(false); },
    });
  }

  invalida(i: NotaItem): boolean {
    const max = this.dados()?.notaMaxima ?? 10;
    return i.nota != null && (i.nota < 0 || i.nota > max);
  }

  salvar(): void {
    const max = this.dados()?.notaMaxima ?? 10;
    if (this.itens().some((i) => this.invalida(i))) {
      this.toast.erro(`Há notas fora do intervalo (0 a ${max}).`);
      return;
    }
    this.salvando.set(true);
    const payload = this.itens().map((i) => ({
      alunoId: i.alunoId,
      nota: i.nota === null || (i.nota as any) === '' ? null : Number(i.nota),
    }));
    this.api.salvarNotas(this.provaId, payload).subscribe({
      next: (r) => {
        this.dados.set(r);
        this.itens.set(r.itens.map((i) => ({ ...i })));
        this.toast.sucesso('Notas salvas!');
        this.salvando.set(false);
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao salvar notas.'); this.salvando.set(false); },
    });
  }

  async notificar(): Promise<void> {
    if (!(await this.confirm.pedir({ titulo: 'Lançar e notificar', mensagem: 'Enviar por e-mail o desempenho aos alunos com nota lançada?', confirmar: 'Enviar' }))) {
      return;
    }
    this.notificando.set(true);
    this.api.notificarNotas(this.provaId).subscribe({
      next: (r) => {
        this.notificando.set(false);
        this.toast.sucesso(`E-mail(s) enviado(s): ${r.enviados}.`);
      },
      error: (e) => {
        this.notificando.set(false);
        this.toast.erro(e?.error?.message || 'Falha ao notificar os alunos.');
      },
    });
  }
}
