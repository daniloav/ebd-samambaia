import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { Aluno, Role, Usuario, UsuarioRequest } from '../../core/models';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="flex-between" style="margin-bottom:1.25rem">
      <div><h2>Usuários</h2><p class="muted">Acessos ao sistema e seus perfis.</p></div>
      <button class="btn" (click)="abrirNovo()">+ Novo usuário</button>
    </div>

    <div class="card">
      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (usuarios().length === 0) {
        <p class="muted text-center">Nenhum usuário cadastrado.</p>
      } @else {
        <div class="tabela-scroll">
          <table class="tabela">
            <thead>
              <tr><th>Usuário</th><th>Perfil</th><th>Aluno vinculado</th><th>Situação</th><th style="width:130px">Ações</th></tr>
            </thead>
            <tbody>
              @for (u of usuarios(); track u.id) {
                <tr>
                  <td>{{ u.username }}</td>
                  <td><span class="badge badge-dourado">{{ rotulo(u.role) }}</span></td>
                  <td>{{ u.alunoNome || '—' }}</td>
                  <td>
                    @if (u.ativo) { <span class="badge badge-verde">Ativo</span> }
                    @else { <span class="badge badge-cinza">Inativo</span> }
                  </td>
                  <td>
                    <button class="btn btn-outline btn-sm" (click)="editar(u)">Editar</button>
                    <button class="btn btn-perigo btn-sm" (click)="excluir(u)">Excluir</button>
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
          <div class="modal-header"><h3>{{ editando() ? 'Editar usuário' : 'Novo usuário' }}</h3></div>
          <div class="modal-body">
            <div class="form-group"><label>Usuário *</label>
              <input type="text" [(ngModel)]="form.username" maxlength="60" autocomplete="off" /></div>
            <div class="form-group">
              <label>Senha {{ editando() ? '(deixe em branco para manter)' : '*' }}</label>
              <input type="password" [(ngModel)]="form.senha" autocomplete="new-password" />
            </div>
            <div class="form-group"><label>Perfil *</label>
              <select [(ngModel)]="form.role">
                <option value="ADMIN">Administrador</option>
                <option value="PROFESSOR">Professor</option>
                <option value="ALUNO">Aluno</option>
              </select>
            </div>
            @if (form.role === 'ALUNO') {
              <div class="form-group"><label>Aluno vinculado</label>
                <select [(ngModel)]="form.alunoId">
                  <option [ngValue]="null">— nenhum —</option>
                  @for (a of alunos(); track a.id) {
                    <option [ngValue]="a.id">{{ a.nome }}{{ a.classeNome ? ' (' + a.classeNome + ')' : '' }}</option>
                  }
                </select>
              </div>
            }
            <div class="form-group" style="flex-direction:row;align-items:center;gap:.5rem">
              <input type="checkbox" id="ativo" [(ngModel)]="form.ativo" />
              <label for="ativo" style="margin:0">Usuário ativo</label>
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
export class UsuariosComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  usuarios = signal<Usuario[]>([]);
  alunos = signal<Aluno[]>([]);
  carregando = signal(true);
  modalAberto = signal(false);
  salvando = signal(false);
  editando = signal<Usuario | null>(null);
  form: UsuarioRequest = this.vazio();

  constructor() {
    this.carregar();
    this.api.listarAlunos(false).subscribe({ next: (l) => this.alunos.set(l), error: () => {} });
  }

  private vazio(): UsuarioRequest {
    return { username: '', senha: '', role: 'PROFESSOR', alunoId: null, ativo: true };
  }

  rotulo(r: Role): string {
    return r === 'ADMIN' ? 'Administrador' : r === 'PROFESSOR' ? 'Professor' : 'Aluno';
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarUsuarios().subscribe({
      next: (l) => { this.usuarios.set(l); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar usuários.'); this.carregando.set(false); },
    });
  }

  abrirNovo(): void { this.editando.set(null); this.form = this.vazio(); this.modalAberto.set(true); }
  editar(u: Usuario): void {
    this.editando.set(u);
    this.form = { username: u.username, senha: '', role: u.role, alunoId: u.alunoId ?? null, ativo: u.ativo };
    this.modalAberto.set(true);
  }
  fechar(): void { this.modalAberto.set(false); }

  salvar(): void {
    if (!this.form.username?.trim()) { this.toast.erro('Informe o usuário.'); return; }
    if (!this.editando() && !this.form.senha?.trim()) { this.toast.erro('Informe a senha.'); return; }
    this.salvando.set(true);
    const payload: UsuarioRequest = {
      username: this.form.username.trim(),
      senha: this.form.senha || null,
      role: this.form.role,
      alunoId: this.form.role === 'ALUNO' ? (this.form.alunoId ?? null) : null,
      ativo: this.form.ativo,
    };
    const alvo = this.editando();
    const req$ = alvo ? this.api.atualizarUsuario(alvo.id, payload) : this.api.criarUsuario(payload);
    req$.subscribe({
      next: () => {
        this.toast.sucesso(alvo ? 'Usuário atualizado!' : 'Usuário criado!');
        this.salvando.set(false); this.fechar(); this.carregar();
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao salvar usuário.'); this.salvando.set(false); },
    });
  }

  excluir(u: Usuario): void {
    if (!confirm(`Excluir o usuário "${u.username}"?`)) return;
    this.api.deletarUsuario(u.id).subscribe({
      next: () => { this.toast.sucesso('Usuário excluído.'); this.carregar(); },
      error: (e) => this.toast.erro(e?.error?.message || 'Erro ao excluir usuário.'),
    });
  }
}
