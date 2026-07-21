import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { ToastService } from '../../core/toast.service';
import { Aluno, BoletimResponse } from '../../core/models';
import { BoletimViewComponent } from './boletim-view.component';

/** Boletim por aluno/trimestre para PROFESSOR e ADMIN (aluno da turma selecionada). */
@Component({
  selector: 'app-boletim',
  standalone: true,
  imports: [FormsModule, BoletimViewComponent],
  styles: [`
    .filtros { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .filtros .form-group { margin: 0; }
  `],
  template: `
    <h2>Boletins</h2>
    <p class="muted">Desempenho do aluno no trimestre — notas, frequência e situação.</p>

    <div class="card" style="margin-bottom:1.5rem">
      <div class="filtros">
        <div class="form-group">
          <label>Aluno</label>
          <select [(ngModel)]="alunoId">
            @for (a of alunos(); track a.id) { <option [ngValue]="a.id">{{ a.nome }}</option> }
          </select>
        </div>
        <div class="form-group">
          <label>Ano</label>
          <input type="number" min="2000" max="2100" [(ngModel)]="ano" style="width:100px" />
        </div>
        <div class="form-group">
          <label>Trimestre</label>
          <select [(ngModel)]="trimestre">
            <option [ngValue]="1">1º (Jan-Mar)</option>
            <option [ngValue]="2">2º (Abr-Jun)</option>
            <option [ngValue]="3">3º (Jul-Set)</option>
            <option [ngValue]="4">4º (Out-Dez)</option>
          </select>
        </div>
        <button class="btn btn-verde" (click)="gerar()" [disabled]="!alunoId || carregando()">
          {{ carregando() ? 'Carregando...' : 'Ver boletim' }}
        </button>
      </div>
    </div>

    @if (boletim(); as b) {
      <app-boletim-view [b]="b" />
    }
  `,
})
export class BoletimComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private classeCtx = inject(ClasseContextService);

  alunos = signal<Aluno[]>([]);
  alunoId: number | null = null;
  ano = new Date().getFullYear();
  trimestre = Math.floor(new Date().getMonth() / 3) + 1;
  boletim = signal<BoletimResponse | null>(null);
  carregando = signal(false);

  constructor() {
    this.classeCtx.carregar();
    // Recarrega a lista de alunos quando a turma selecionada muda.
    effect(() => {
      const cid = this.classeCtx.selecionadaId();
      this.carregarAlunos(cid);
    }, { allowSignalWrites: true });
  }

  private carregarAlunos(classeId: number | null): void {
    this.api.listarAlunos(true, classeId).subscribe({
      next: (l) => {
        this.alunos.set(l);
        if (!l.some((a) => a.id === this.alunoId)) {
          this.alunoId = l.length ? l[0].id : null;
        }
        this.boletim.set(null);
      },
      error: () => this.toast.erro('Falha ao carregar alunos.'),
    });
  }

  gerar(): void {
    if (!this.alunoId) { return; }
    this.carregando.set(true);
    this.api.boletim(this.alunoId, this.ano, this.trimestre).subscribe({
      next: (b) => { this.boletim.set(b); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao gerar o boletim.'); this.carregando.set(false); },
    });
  }
}
