import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../core/confirm.service';
import { Aluno, Classe, Usuario, UsuarioRequest } from '../../core/models';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="flex-between" style="margin-bottom:1.25rem">
      <div><h2>Usuários</h2><p class="muted">Acessos ao sistema, perfis e vínculos.</p></div>
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
              <tr><th>Usuário</th><th>Perfil</th><th>Vínculo</th><th>Situação</th><th style="width:130px">Ações</th></tr>
            </thead>
            <tbody>
              @for (u of usuarios(); track u.id) {
                <tr>
                  <td>{{ u.username }}</td>
                  <td>@if (u.ehAdmin) { <span class="badge badge-dourado">Admin</span> }@if (u.ehProfessor) { <span class="badge badge-dourado">Professor</span> }@if (u.ehAluno) { <span class="badge badge-dourado">Aluno</span> }@if (u.ehTesoureiro) { <span class="badge badge-azul" title="Tesoureiro">💰 Tes.</span> }@if (u.ehLider) { <span class="badge badge-azul" title="Líder">Líder</span> }@if (!u.ehAdmin && !u.ehProfessor && !u.ehAluno && !u.ehTesoureiro && !u.ehLider) { <span class="badge badge-cinza">sem papel</span> }</td>
                  <td>{{ vinculo(u) }}</td>
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
            <div class="form-group"><label>Usuário (login) *</label>
              <input type="text" [(ngModel)]="form.username" maxlength="60" autocomplete="off" />
              <small class="muted">Só minúsculas, números, ponto, hífen ou sublinhado (ex.: joao.silva).</small></div>
            <div class="form-group">
              <label>Senha {{ editando() ? '(deixe em branco para manter)' : '*' }}</label>
              <input type="password" [(ngModel)]="form.senha" autocomplete="new-password" />
            </div>
            <div class="form-group"><label>E-mail {{ form.ehProfessor ? '(recebe avisos de visitantes)' : '' }}</label>
              <input type="email" [(ngModel)]="form.email" maxlength="150" autocomplete="off" /></div>
            <div class="form-group">
              <label>Papéis * (um usuário pode acumular vários)</label>
              <label class="check-inline"><input type="checkbox" [(ngModel)]="form.ehAdmin" /> Administrador</label>
              <label class="check-inline"><input type="checkbox" [(ngModel)]="form.ehProfessor" /> Professor</label>
              <label class="check-inline"><input type="checkbox" [(ngModel)]="form.ehAluno" /> Aluno</label>
              <label class="check-inline"><input type="checkbox" [(ngModel)]="form.ehTesoureiro" /> Tesoureiro</label>
              <label class="check-inline"><input type="checkbox" [(ngModel)]="form.ehLider" /> Líder de ministério</label>
              @if (form.ehAdmin) { <small class="muted">O administrador enxerga e faz tudo (inclusive tesouraria).</small> }
            </div>

            @if (form.ehProfessor) {
              <div class="form-group">
                <label>Turmas do professor</label>
                @if (classes().length) {
                  <div style="display:flex;flex-direction:column;gap:.35rem;max-height:180px;overflow:auto;
                              border:1px solid #e2e8f0;border-radius:8px;padding:.6rem">
                    @for (c of classes(); track c.id) {
                      <label style="display:flex;align-items:center;gap:.5rem;margin:0;font-weight:400">
                        <input type="checkbox" [checked]="temClasse(c.id)"
                               (change)="toggleClasse(c.id, $any($event.target).checked)" />
                        {{ c.nome }}
                      </label>
                    }
                  </div>
                  <span class="muted" style="font-size:.8rem">O professor só acessa a chamada e os desafios das turmas marcadas.</span>
                } @else {
                  <span class="muted">Nenhuma turma cadastrada.</span>
                }
              </div>
            }

            @if (form.ehAluno || form.ehProfessor) {
              <div class="form-group"><label>Aluno vinculado</label>
                <select aria-label="Aluno vinculado" [(ngModel)]="form.alunoId">
                  <option [ngValue]="null">— nenhum —</option>
                  @for (a of alunos(); track a.id) {
                    <option [ngValue]="a.id">{{ a.nome }}{{ a.classeNome ? ' (' + a.classeNome + ')' : '' }}</option>
                  }
                </select>
                <span class="muted" style="font-size:.8rem">Liga o login ao registro de aluno: dá a visão própria (frequência/provas) e, para professor, é o correlato que não conta na aula que ele dá.</span>
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
  private confirm = inject(ConfirmService);

  usuarios = signal<Usuario[]>([]);
  alunos = signal<Aluno[]>([]);
  classes = signal<Classe[]>([]);
  carregando = signal(true);
  modalAberto = signal(false);
  salvando = signal(false);
  editando = signal<Usuario | null>(null);
  form: UsuarioRequest = this.vazio();

  constructor() {
    this.carregar();
    this.api.listarAlunos(false).subscribe({ next: (l) => this.alunos.set(l), error: () => {} });
    this.api.listarClasses(false).subscribe({ next: (l) => this.classes.set(l), error: () => {} });
  }

  private vazio(): UsuarioRequest {
    return { username: '', senha: '', ehAdmin: false, ehProfessor: true, ehAluno: false, alunoId: null, classeIds: [], email: '', ativo: true, ehTesoureiro: false, ehLider: false };
  }

  vinculo(u: Usuario): string {
    const partes: string[] = [];
    if (u.ehProfessor) {
      partes.push(u.classes?.length ? u.classes.map((c) => c.nome).join(', ') : 'sem turma');
    }
    if (u.ehAluno || (u.ehProfessor && u.alunoNome)) {
      partes.push(u.alunoNome || 'sem aluno');
    }
    return partes.length ? partes.join(' · ') : '—';
  }

  temClasse(id: number): boolean {
    return (this.form.classeIds ?? []).includes(id);
  }
  toggleClasse(id: number, marcado: boolean): void {
    const set = new Set(this.form.classeIds ?? []);
    if (marcado) { set.add(id); } else { set.delete(id); }
    this.form.classeIds = Array.from(set);
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
    this.form = {
      username: u.username, senha: '',
      ehAdmin: u.ehAdmin, ehProfessor: u.ehProfessor, ehAluno: u.ehAluno,
      alunoId: u.alunoId ?? null,
      classeIds: (u.classes ?? []).map((c) => c.id),
      email: u.email ?? '',
      ativo: u.ativo,
      ehTesoureiro: !!u.ehTesoureiro,
      ehLider: !!u.ehLider,
    };
    this.modalAberto.set(true);
  }
  fechar(): void { this.modalAberto.set(false); }

  salvar(): void {
    if (!this.form.username?.trim()) { this.toast.erro('Informe o usuário.'); return; }
    if (!this.editando() && !this.form.senha?.trim()) { this.toast.erro('Informe a senha.'); return; }
    if (!this.form.ehAdmin && !this.form.ehProfessor && !this.form.ehAluno && !this.form.ehTesoureiro && !this.form.ehLider) {
      this.toast.erro('Selecione ao menos um papel.'); return;
    }
    this.salvando.set(true);
    const payload: UsuarioRequest = {
      username: this.form.username.trim(),
      senha: this.form.senha || null,
      ehAdmin: !!this.form.ehAdmin,
      ehProfessor: !!this.form.ehProfessor,
      ehAluno: !!this.form.ehAluno,
      alunoId: (this.form.ehAluno || this.form.ehProfessor) ? (this.form.alunoId ?? null) : null,
      classeIds: this.form.ehProfessor ? (this.form.classeIds ?? []) : null,
      email: this.form.email || null,
      ativo: this.form.ativo,
      ehTesoureiro: !!this.form.ehTesoureiro,
      ehLider: !!this.form.ehLider,
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

  async excluir(u: Usuario): Promise<void> {
    if (!(await this.confirm.pedir({ titulo: 'Excluir usuário', mensagem: `Excluir o usuário "${u.username}"?`, confirmar: 'Excluir', perigo: true }))) { return; }
    this.api.deletarUsuario(u.id).subscribe({
      next: () => { this.toast.sucesso('Usuário excluído.'); this.carregar(); },
      error: (e) => this.toast.erro(e?.error?.message || 'Erro ao excluir usuário.'),
    });
  }
}
