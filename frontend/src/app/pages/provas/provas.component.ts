import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../core/confirm.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { Prova, ProvaRequest } from '../../core/models';

@Component({
  selector: 'app-provas',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <div class="flex-between" style="margin-bottom:1.25rem">
      <div><h2>Provas</h2><p class="muted">Avaliações da classe (submódulo dos desafios).</p></div>
      @if (auth.isAdmin() || auth.isProfessor()) {
        <button class="btn" (click)="abrirNovo()">+ Nova prova</button>
      }
    </div>

    <div class="card">
      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (provas().length === 0) {
        <p class="muted text-center">Nenhuma prova cadastrada.</p>
      } @else {
        <div class="tabela-scroll">
          <table class="tabela">
            <thead>
              <tr><th>Título</th><th>Data</th><th>Nota máxima</th><th style="width:230px">Ações</th></tr>
            </thead>
            <tbody>
              @for (p of provas(); track p.id) {
                <tr>
                  <td>{{ p.titulo }}</td>
                  <td>{{ p.data | date:'dd/MM/yyyy' }}</td>
                  <td>{{ p.notaMaxima }}</td>
                  <td>
                    <a class="btn btn-dourado btn-sm" [routerLink]="['/provas', p.id, 'notas']">Lançar notas</a>
                    @if (auth.isAdmin() || auth.isProfessor()) {
                      <button class="btn btn-outline btn-sm" (click)="editar(p)">Editar</button>
                    }
                    @if (auth.isAdmin()) {
                      <button class="btn btn-perigo btn-sm" (click)="excluir(p)">Excluir</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    @if (modalAberto()) {
      <div class="modal-backdrop" (click)="fechar()">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header"><h3>{{ editando() ? 'Editar prova' : 'Nova prova' }}</h3></div>
          <div class="modal-body">
            <div class="form-group"><label>Título *</label>
              <input type="text" [(ngModel)]="form.titulo" maxlength="200" /></div>
            <div class="form-group"><label>Data *</label>
              <input type="date" [(ngModel)]="form.data" /></div>
            <div class="form-group"><label>Nota máxima *</label>
              <input type="number" [(ngModel)]="form.notaMaxima" min="0.5" step="0.5" /></div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-outline" (click)="fechar()">Cancelar</button>
            <button class="btn btn-verde" (click)="salvar()" [disabled]="salvando()">
              {{ salvando() ? 'Salvando...' : 'Salvar' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ProvasComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  auth = inject(AuthService);
  private classeCtx = inject(ClasseContextService);

  provas = signal<Prova[]>([]);
  carregando = signal(true);
  modalAberto = signal(false);
  salvando = signal(false);
  editando = signal<Prova | null>(null);
  form: ProvaRequest = this.formVazio();

  constructor() { effect(() => { this.classeCtx.selecionadaId(); this.carregar(); }, { allowSignalWrites: true }); }

  private formVazio(): ProvaRequest {
    return { titulo: '', data: '', notaMaxima: 10 };
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarProvas(this.classeCtx.selecionadaId()).subscribe({
      next: (l) => { this.provas.set(l); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar provas.'); this.carregando.set(false); },
    });
  }

  abrirNovo(): void { this.editando.set(null); this.form = this.formVazio(); this.modalAberto.set(true); }
  editar(p: Prova): void {
    this.editando.set(p);
    this.form = { titulo: p.titulo, data: p.data, notaMaxima: p.notaMaxima };
    this.modalAberto.set(true);
  }
  fechar(): void { this.modalAberto.set(false); }

  salvar(): void {
    if (!this.form.titulo?.trim() || !this.form.data) { this.toast.erro('Preencha título e data.'); return; }
    const classeId = this.classeCtx.selecionadaId();
    if (!classeId) { this.toast.erro('Selecione uma turma no menu.'); return; }
    this.salvando.set(true);
    const payload = { ...this.form, classeId };
    const alvo = this.editando();
    const req$ = alvo ? this.api.atualizarProva(alvo.id, payload) : this.api.criarProva(payload);
    req$.subscribe({
      next: () => {
        this.toast.sucesso(alvo ? 'Prova atualizada!' : 'Prova criada!');
        this.salvando.set(false); this.fechar(); this.carregar();
      },
      error: () => { this.toast.erro('Erro ao salvar prova.'); this.salvando.set(false); },
    });
  }

  async excluir(p: Prova): Promise<void> {
    if (!(await this.confirm.pedir({ titulo: 'Excluir prova', mensagem: `Excluir a prova "${p.titulo}"? As notas lançadas serão removidas.`, confirmar: 'Excluir', perigo: true }))) { return; }
    this.api.deletarProva(p.id).subscribe({
      next: () => { this.toast.sucesso('Prova excluída.'); this.carregar(); },
      error: () => this.toast.erro('Erro ao excluir prova.'),
    });
  }
}
