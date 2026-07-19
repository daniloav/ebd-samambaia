import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { Classe, ClasseRequest } from '../../core/models';

@Component({
  selector: 'app-classes',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="flex-between" style="margin-bottom:1.25rem">
      <div><h2>Classes</h2><p class="muted">Turmas da Escola Bíblica Dominical.</p></div>
      <button class="btn" (click)="abrirNovo()">+ Nova classe</button>
    </div>

    <div class="card">
      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (classes().length === 0) {
        <p class="muted text-center">Nenhuma classe cadastrada.</p>
      } @else {
        <div class="tabela-scroll">
          <table class="tabela">
            <thead>
              <tr><th>Nome</th><th>Descrição</th><th>Situação</th><th style="width:130px">Ações</th></tr>
            </thead>
            <tbody>
              @for (c of classes(); track c.id) {
                <tr>
                  <td>{{ c.nome }}</td>
                  <td>{{ c.descricao || '—' }}</td>
                  <td>
                    @if (c.ativo) { <span class="badge badge-verde">Ativa</span> }
                    @else { <span class="badge badge-cinza">Inativa</span> }
                  </td>
                  <td>
                    <button class="btn btn-outline btn-sm" (click)="editar(c)">Editar</button>
                    <button class="btn btn-perigo btn-sm" (click)="excluir(c)">Excluir</button>
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
          <div class="modal-header"><h3>{{ editando() ? 'Editar classe' : 'Nova classe' }}</h3></div>
          <div class="modal-body">
            <div class="form-group"><label>Nome *</label>
              <input type="text" [(ngModel)]="form.nome" maxlength="120" /></div>
            <div class="form-group"><label>Descrição</label>
              <input type="text" [(ngModel)]="form.descricao" maxlength="300" /></div>
            <div class="form-group" style="flex-direction:row;align-items:center;gap:.5rem">
              <input type="checkbox" id="ativo" [(ngModel)]="form.ativo" />
              <label for="ativo" style="margin:0">Classe ativa</label>
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
export class ClassesComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private classeCtx = inject(ClasseContextService);

  classes = signal<Classe[]>([]);
  carregando = signal(true);
  modalAberto = signal(false);
  salvando = signal(false);
  editando = signal<Classe | null>(null);
  form: ClasseRequest = this.vazio();

  constructor() { this.carregar(); }

  private vazio(): ClasseRequest { return { nome: '', descricao: '', ativo: true }; }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarClasses(false).subscribe({
      next: (l) => { this.classes.set(l); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar classes.'); this.carregando.set(false); },
    });
  }

  abrirNovo(): void { this.editando.set(null); this.form = this.vazio(); this.modalAberto.set(true); }
  editar(c: Classe): void {
    this.editando.set(c);
    this.form = { nome: c.nome, descricao: c.descricao ?? '', ativo: c.ativo };
    this.modalAberto.set(true);
  }
  fechar(): void { this.modalAberto.set(false); }

  salvar(): void {
    if (!this.form.nome?.trim()) { this.toast.erro('Informe o nome da classe.'); return; }
    this.salvando.set(true);
    const alvo = this.editando();
    const req$ = alvo ? this.api.atualizarClasse(alvo.id, this.form) : this.api.criarClasse(this.form);
    req$.subscribe({
      next: () => {
        this.toast.sucesso(alvo ? 'Classe atualizada!' : 'Classe criada!');
        this.salvando.set(false); this.fechar();
        this.carregar();
        this.classeCtx.carregar();
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao salvar classe.'); this.salvando.set(false); },
    });
  }

  excluir(c: Classe): void {
    if (!confirm(`Excluir a classe "${c.nome}"?`)) return;
    this.api.deletarClasse(c.id).subscribe({
      next: () => { this.toast.sucesso('Classe excluída.'); this.carregar(); this.classeCtx.carregar(); },
      error: (e) => this.toast.erro(e?.error?.message || 'Erro ao excluir classe.'),
    });
  }
}
