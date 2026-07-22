import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { MinhaProva, StatusProva } from '../../core/models';

/** Lista das provas online (quiz) da turma do aluno, com status e nota. */
@Component({
  selector: 'app-minhas-provas',
  standalone: true,
  imports: [DatePipe, RouterLink],
  styles: [`
    .lista { display: grid; gap: .8rem; }
    .prova { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
      border: 1px solid var(--cinza-borda); border-radius: 10px; padding: 1rem 1.1rem; }
    .prova .info { flex: 1; min-width: 200px; }
    .prova .titulo { font-weight: 700; color: var(--titulo); }
    .prova .sub { font-size: .85rem; color: var(--cinza-texto); }
    .badge { font-size: .78rem; font-weight: 700; padding: .2rem .6rem; border-radius: 999px; white-space: nowrap; }
    .b-disp { background: #dcfce7; color: #166534; }
    .b-resp { background: #dbeafe; color: #1e40af; }
    .b-fech { background: #fee2e2; color: #991b1b; }
    .b-fut  { background: #fef9c3; color: #854d0e; }
    .nota { font-weight: 800; color: var(--titulo); font-size: 1.05rem; }
  `],
  template: `
    <h2>Minhas provas</h2>
    <p class="muted">Quizzes online da sua turma — respondidos pela tela e corrigidos na hora.</p>

    @if (carregando()) {
      <div class="card muted">Carregando...</div>
    } @else if (provas().length === 0) {
      <div class="card muted text-center">Nenhuma prova online disponível no momento.</div>
    } @else {
      <div class="lista">
        @for (p of provas(); track p.id) {
          <div class="prova">
            <div class="info">
              <div class="titulo">{{ p.titulo }}</div>
              <div class="sub">
                {{ p.data | date:'dd/MM/yyyy' }} · {{ p.numQuestoes }} questão(ões) · vale {{ p.notaMaxima }}
                @if (p.fechaEm && p.status === 'DISPONIVEL') { · fecha {{ p.fechaEm | date:'dd/MM HH:mm' }} }
                @if (p.abreEm && p.status === 'FUTURA') { · abre {{ p.abreEm | date:'dd/MM HH:mm' }} }
              </div>
            </div>
            <span class="badge" [class]="classeBadge(p.status)">{{ rotulo(p.status) }}</span>
            @if (p.status === 'RESPONDIDA') {
              <span class="nota">{{ p.nota }} / {{ p.notaMaxima }}</span>
              <a class="btn btn-outline btn-sm" [routerLink]="['/minhas-provas', p.id]">Ver resultado</a>
            } @else if (p.status === 'DISPONIVEL') {
              <a class="btn" [routerLink]="['/minhas-provas', p.id]">Responder</a>
            }
          </div>
        }
      </div>
    }
  `,
})
export class MinhasProvasComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  provas = signal<MinhaProva[]>([]);
  carregando = signal(true);

  constructor() {
    this.api.minhasProvas().subscribe({
      next: (ps) => { this.provas.set(ps); this.carregando.set(false); },
      error: () => { this.toast.erro('Não foi possível carregar suas provas.'); this.carregando.set(false); },
    });
  }

  rotulo(s: StatusProva): string {
    return { DISPONIVEL: 'Disponível', RESPONDIDA: 'Respondida', FECHADA: 'Encerrada', FUTURA: 'Em breve' }[s];
  }
  classeBadge(s: StatusProva): string {
    return { DISPONIVEL: 'b-disp', RESPONDIDA: 'b-resp', FECHADA: 'b-fech', FUTURA: 'b-fut' }[s];
  }
}
