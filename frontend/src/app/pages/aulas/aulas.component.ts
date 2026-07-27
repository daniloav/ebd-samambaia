import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../core/confirm.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { Aula, AulaRequest, Professor } from '../../core/models';

@Component({
  selector: 'app-aulas',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <div class="flex-between" style="margin-bottom:1.25rem">
      <div><h2>Aulas</h2><p class="muted">Encontros da turma (cada domingo/data da EBD).</p></div>
      <button class="btn" (click)="abrirNovo()">+ Nova aula</button>
    </div>

    <div class="card">
      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (aulas().length === 0) {
        <p class="muted text-center">Nenhuma aula cadastrada nesta turma.</p>
      } @else {
        <div class="tabela-scroll">
          <table class="tabela">
            <thead>
              <tr><th style="width:130px">Data</th><th>Tema</th><th style="width:290px">Ações</th></tr>
            </thead>
            <tbody>
              @for (a of aulas(); track a.id) {
                <tr>
                  <td>{{ a.data | date:'dd/MM/yyyy' }}</td>
                  <td>{{ a.tema || '—' }}
                    @if (a.professorNome) { <br><small class="muted">👩‍🏫 Professor: {{ a.professorNome }}</small> }
                  </td>
                  <td>
                    <a class="btn btn-dourado btn-sm" routerLink="/chamada">Fazer chamada</a>
                    <button class="btn btn-outline btn-sm" (click)="editar(a)">Editar</button>
                    @if (auth.isAdmin()) {
                      <button class="btn btn-perigo btn-sm" (click)="excluir(a)">Excluir</button>
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
          <div class="modal-header"><h3>{{ editando() ? 'Editar aula' : 'Nova aula' }}</h3></div>
          <div class="modal-body">
            <div class="form-group"><label>Data *</label>
              <input type="date" [(ngModel)]="form.data" /></div>
            <div class="form-group"><label>Tema</label>
              <input type="text" [(ngModel)]="form.tema" maxlength="200" placeholder="Ex.: A graça de Deus" /></div>
            <div class="form-group"><label>Professor da aula</label>
              <select [(ngModel)]="form.professorId">
                <option [ngValue]="null">— sem professor definido —</option>
                @for (pr of professores(); track pr.id) { <option [ngValue]="pr.id">{{ pr.nome }}</option> }
              </select>
              <small class="muted">Quem deu a aula não é contabilizado na chamada nem no ranking desta aula.</small></div>
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
export class AulasComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  auth = inject(AuthService);
  private classeCtx = inject(ClasseContextService);

  aulas = signal<Aula[]>([]);
  professores = signal<Professor[]>([]);
  carregando = signal(true);
  modalAberto = signal(false);
  salvando = signal(false);
  editando = signal<Aula | null>(null);
  form: AulaRequest = this.vazio();

  constructor() {
    effect(() => { this.classeCtx.selecionadaId(); this.carregar(); this.carregarProfessores(); }, { allowSignalWrites: true });
  }

  private vazio(): AulaRequest { return { data: '', tema: '', professorId: null }; }

  private carregarProfessores(): void {
    const cid = this.classeCtx.selecionadaId();
    if (!cid) { this.professores.set([]); return; }
    this.api.listarProfessores(cid).subscribe({ next: (l) => this.professores.set(l), error: () => this.professores.set([]) });
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarAulas(this.classeCtx.selecionadaId()).subscribe({
      next: (l) => { this.aulas.set(l); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar aulas.'); this.carregando.set(false); },
    });
  }

  abrirNovo(): void { this.editando.set(null); this.form = this.vazio(); this.modalAberto.set(true); }
  editar(a: Aula): void {
    this.editando.set(a);
    this.form = { data: a.data, tema: a.tema ?? '', professorId: a.professorId ?? null };
    this.modalAberto.set(true);
  }
  fechar(): void { this.modalAberto.set(false); }

  salvar(): void {
    if (!this.form.data) { this.toast.erro('Informe a data da aula.'); return; }
    const classeId = this.classeCtx.selecionadaId();
    if (!classeId) { this.toast.erro('Selecione uma turma no menu.'); return; }
    this.salvando.set(true);
    const payload: AulaRequest = { classeId, data: this.form.data, tema: this.form.tema || null, professorId: this.form.professorId ?? null };
    const alvo = this.editando();
    const req$ = alvo ? this.api.atualizarAula(alvo.id, payload) : this.api.criarAula(payload);
    req$.subscribe({
      next: () => {
        this.toast.sucesso(alvo ? 'Aula atualizada!' : 'Aula criada!');
        this.salvando.set(false); this.fechar(); this.carregar();
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao salvar aula.'); this.salvando.set(false); },
    });
  }

  async excluir(a: Aula): Promise<void> {
    const quando = new Date(a.data + 'T00:00:00').toLocaleDateString('pt-BR');
    if (!(await this.confirm.pedir({ titulo: 'Excluir aula', mensagem: `Excluir a aula de ${quando}? A chamada dessa aula será removida.`, confirmar: 'Excluir', perigo: true }))) { return; }
    this.api.deletarAula(a.id).subscribe({
      next: () => { this.toast.sucesso('Aula excluída.'); this.carregar(); },
      error: (e) => this.toast.erro(e?.error?.message || 'Erro ao excluir aula.'),
    });
  }
}
