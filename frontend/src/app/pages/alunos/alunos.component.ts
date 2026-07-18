import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { Aluno, AlunoRequest } from '../../core/models';

@Component({
  selector: 'app-alunos',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="flex-between" style="margin-bottom:1.25rem">
      <div><h2>Alunos</h2><p class="muted">Cadastro da classe de adultos.</p></div>
      @if (auth.isAdmin()) {
        <button class="btn" (click)="abrirNovo()">+ Novo aluno</button>
      }
    </div>

    <div class="card">
      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (alunos().length === 0) {
        <p class="muted text-center">Nenhum aluno cadastrado ainda.</p>
      } @else {
        <div class="tabela-scroll">
          <table class="tabela">
            <thead>
              <tr>
                <th>Nome</th><th>Telefone</th><th>Nascimento</th><th>Situação</th>
                @if (auth.isAdmin()) { <th style="width:130px">Ações</th> }
              </tr>
            </thead>
            <tbody>
              @for (a of alunos(); track a.id) {
                <tr>
                  <td>{{ a.nome }}</td>
                  <td>{{ a.telefone || '—' }}</td>
                  <td>{{ a.dataNascimento ? (a.dataNascimento | date:'dd/MM/yyyy') : '—' }}</td>
                  <td>
                    @if (a.ativo) { <span class="badge badge-verde">Ativo</span> }
                    @else { <span class="badge badge-cinza">Inativo</span> }
                  </td>
                  @if (auth.isAdmin()) {
                    <td>
                      <button class="btn btn-outline btn-sm" (click)="editar(a)">Editar</button>
                      <button class="btn btn-perigo btn-sm" (click)="confirmarExclusao(a)">Excluir</button>
                    </td>
                  }
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
          <div class="modal-header"><h3>{{ editando() ? 'Editar aluno' : 'Novo aluno' }}</h3></div>
          <div class="modal-body">
            <div class="form-group">
              <label>Nome *</label>
              <input type="text" [(ngModel)]="form.nome" maxlength="120" />
            </div>
            <div class="form-group">
              <label>Telefone</label>
              <input type="tel" [(ngModel)]="form.telefone" maxlength="20" placeholder="(61) 90000-0000" />
            </div>
            <div class="form-group">
              <label>Data de nascimento</label>
              <input type="date" [(ngModel)]="form.dataNascimento" />
            </div>
            <div class="form-group" style="flex-direction:row;align-items:center;gap:.5rem">
              <input type="checkbox" id="ativo" [(ngModel)]="form.ativo" />
              <label for="ativo" style="margin:0">Aluno ativo</label>
            </div>
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
export class AlunosComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  alunos = signal<Aluno[]>([]);
  carregando = signal(true);
  modalAberto = signal(false);
  salvando = signal(false);
  editando = signal<Aluno | null>(null);
  form: AlunoRequest = this.formVazio();

  constructor() {
    this.carregar();
  }

  private formVazio(): AlunoRequest {
    return { nome: '', telefone: '', dataNascimento: null, ativo: true };
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarAlunos().subscribe({
      next: (l) => { this.alunos.set(l); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar alunos.'); this.carregando.set(false); },
    });
  }

  abrirNovo(): void {
    this.editando.set(null);
    this.form = this.formVazio();
    this.modalAberto.set(true);
  }

  editar(a: Aluno): void {
    this.editando.set(a);
    this.form = { nome: a.nome, telefone: a.telefone ?? '', dataNascimento: a.dataNascimento ?? null, ativo: a.ativo };
    this.modalAberto.set(true);
  }

  fechar(): void { this.modalAberto.set(false); }

  salvar(): void {
    if (!this.form.nome?.trim()) { this.toast.erro('Informe o nome do aluno.'); return; }
    this.salvando.set(true);
    const payload: AlunoRequest = {
      nome: this.form.nome.trim(),
      telefone: this.form.telefone || null,
      dataNascimento: this.form.dataNascimento || null,
      ativo: this.form.ativo,
    };
    const alvo = this.editando();
    const req$ = alvo ? this.api.atualizarAluno(alvo.id, payload) : this.api.criarAluno(payload);
    req$.subscribe({
      next: () => {
        this.toast.sucesso(alvo ? 'Aluno atualizado!' : 'Aluno cadastrado!');
        this.salvando.set(false);
        this.fechar();
        this.carregar();
      },
      error: () => { this.toast.erro('Erro ao salvar aluno.'); this.salvando.set(false); },
    });
  }

  confirmarExclusao(a: Aluno): void {
    if (!confirm(`Excluir o aluno "${a.nome}"? Isso remove também seus registros de chamada e notas.`)) return;
    this.api.deletarAluno(a.id).subscribe({
      next: () => { this.toast.sucesso('Aluno excluído.'); this.carregar(); },
      error: () => this.toast.erro('Erro ao excluir aluno.'),
    });
  }
}
