import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { Auditoria, AcaoAuditoria } from '../../core/models';

/** Auditoria de ações (ADMIN): quem criou/alterou/excluiu aluno, aula, prova ou usuário. */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .filtros { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .filtros .form-group { margin: 0; }
    .tag { font-size: .75rem; font-weight: 700; padding: .15rem .55rem; border-radius: 999px; white-space: nowrap; }
    .t-criar { background: #dcfce7; color: #166534; }
    .t-atualizar { background: #dbeafe; color: #1e40af; }
    .t-excluir { background: #fee2e2; color: #991b1b; }
    .reg { color: var(--cinza-texto); font-size: .8rem; }
  `],
  template: `
    <h2>Auditoria de ações</h2>
    <p class="muted">Registro de quem criou, alterou ou excluiu alunos, aulas, provas e usuários.</p>

    <div class="card">
      <div class="filtros">
        <div class="form-group">
          <label>Entidade</label>
          <select [(ngModel)]="entidade">
            <option [ngValue]="null">Todas</option>
            <option value="ALUNO">Aluno</option>
            <option value="AULA">Aula</option>
            <option value="PROVA">Prova</option>
            <option value="USUARIO">Usuário</option>
          </select>
        </div>
        <div class="form-group"><label>Início</label><input type="date" [(ngModel)]="inicio" /></div>
        <div class="form-group"><label>Fim</label><input type="date" [(ngModel)]="fim" /></div>
        <button class="btn" (click)="filtrar()" [disabled]="carregando()">
          {{ carregando() ? 'Carregando...' : 'Filtrar' }}
        </button>
        @if (inicio || fim || entidade) {
          <button class="btn btn-outline" (click)="limpar()">Limpar</button>
        }
      </div>

      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (itens().length === 0) {
        <p class="muted text-center">Nenhuma ação registrada para o filtro.</p>
      } @else {
        <p class="muted" style="margin:0 0 .8rem">{{ itens().length }} registro(s) — mais recentes primeiro.</p>
        <div class="tabela-scroll">
          <table class="tabela tabela-cards">
            <thead>
              <tr><th>Data / hora</th><th>Usuário</th><th>Ação</th><th>Entidade</th><th>Registro</th></tr>
            </thead>
            <tbody>
              @for (a of itens(); track a.id) {
                <tr>
                  <td data-label="Data / hora">{{ a.dataHora | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td data-label="Usuário">{{ a.usuario }}</td>
                  <td data-label="Ação"><span class="tag" [class]="classe(a.acao)">{{ rotuloAcao(a.acao) }}</span></td>
                  <td data-label="Entidade">{{ rotuloEntidade(a.entidade) }}</td>
                  <td data-label="Registro">{{ a.descricao || '—' }} @if (a.entidadeId) { <span class="reg">#{{ a.entidadeId }}</span> }</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class AuditoriaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  entidade: string | null = null;
  inicio = '';
  fim = '';
  itens = signal<Auditoria[]>([]);
  carregando = signal(false);

  constructor() { this.filtrar(); }

  filtrar(): void {
    this.carregando.set(true);
    this.api.listarAuditoria(this.entidade, this.inicio || null, this.fim || null).subscribe({
      next: (l) => { this.itens.set(l); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao carregar a auditoria.'); this.carregando.set(false); },
    });
  }

  limpar(): void {
    this.entidade = null; this.inicio = ''; this.fim = '';
    this.filtrar();
  }

  rotuloAcao(a: AcaoAuditoria): string {
    return { CRIAR: 'Criou', ATUALIZAR: 'Alterou', EXCLUIR: 'Excluiu' }[a];
  }
  classe(a: AcaoAuditoria): string {
    return { CRIAR: 't-criar', ATUALIZAR: 't-atualizar', EXCLUIR: 't-excluir' }[a];
  }
  rotuloEntidade(e: string): string {
    return ({ ALUNO: 'Aluno', AULA: 'Aula', PROVA: 'Prova', USUARIO: 'Usuário' } as Record<string, string>)[e] || e;
  }
}
