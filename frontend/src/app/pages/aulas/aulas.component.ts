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
              <tr><th style="width:130px">Data</th><th>Tema</th><th style="width:390px">Ações</th></tr>
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
                    <button class="btn btn-outline btn-sm" (click)="abrirComplementar(a)"
                      title="Continuar esta aula no próximo domingo, empurrando a agenda seguinte">Desdobrar</button>
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
              <select aria-label="Professor" [(ngModel)]="form.professorId">
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

    @if (modalComplementarAberto()) {
      <div class="modal-backdrop" (click)="fecharComplementar()">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header"><h3>Desdobrar aula (aula complementar)</h3></div>
          <div class="modal-body">
            <p class="muted" style="margin-top:0">
              Aula de origem:
              <b>{{ origem()?.data | date:'dd/MM/yyyy' }}</b>{{ origem()?.tema ? ' · ' + origem()?.tema : '' }}.
            </p>
            <div class="card" style="background:var(--cor-fundo-suave,#f7f7f5);padding:.75rem 1rem;margin-bottom:1rem">
              A aula complementar será criada em <b>{{ novaData() | date:'dd/MM/yyyy' }}</b> (próximo domingo).
              @if (qtdMovidas() > 0) {
                <br>As <b>{{ qtdMovidas() }}</b> aula(s) seguinte(s) da turma serão movidas <b>+7 dias</b>.
              } @else {
                <br>Não há aulas posteriores — nada será movido.
              }
            </div>
            <div class="form-group"><label>Tema da aula complementar</label>
              <input type="text" [(ngModel)]="formComplementar.tema" maxlength="200"
                placeholder="Ex.: A graça de Deus (continuação)" /></div>
            <div class="form-group"><label>Professor da aula complementar</label>
              <select aria-label="Professor da aula complementar" [(ngModel)]="formComplementar.professorId">
                <option [ngValue]="null">— sem professor definido —</option>
                @for (pr of professores(); track pr.id) { <option [ngValue]="pr.id">{{ pr.nome }}</option> }
              </select></div>
            <small class="muted">Aulas que já tiveram chamada levam as presenças para a nova data — a função é
              pensada para a agenda futura ainda sem chamada.</small>
          </div>
          <div class="modal-footer">
            <button class="btn btn-outline" (click)="fecharComplementar()">Cancelar</button>
            <button class="btn btn-verde" (click)="confirmarComplementar()" [disabled]="complementando()">
              {{ complementando() ? 'Processando...' : 'Criar e empurrar agenda' }}
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

  // Desdobramento (aula complementar + empurrão da agenda)
  modalComplementarAberto = signal(false);
  complementando = signal(false);
  origem = signal<Aula | null>(null);
  formComplementar: { tema: string | null; professorId: number | null } = { tema: '', professorId: null };

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

  // ---- Desdobramento ----
  abrirComplementar(a: Aula): void {
    this.origem.set(a);
    this.formComplementar = {
      tema: a.tema ? `${a.tema} (continuação)` : '',
      professorId: a.professorId ?? null,
    };
    this.modalComplementarAberto.set(true);
  }
  fecharComplementar(): void { this.modalComplementarAberto.set(false); this.origem.set(null); }

  /** Próximo domingo = data da aula de origem + 7 dias. */
  novaData(): Date | null {
    const o = this.origem();
    if (!o) { return null; }
    const d = new Date(o.data + 'T00:00:00');
    d.setDate(d.getDate() + 7);
    return d;
  }

  /** Quantas aulas da turma serão empurradas (as com data posterior à de origem). */
  qtdMovidas(): number {
    const o = this.origem();
    if (!o) { return 0; }
    return this.aulas().filter((x) => x.data > o.data).length;
  }

  confirmarComplementar(): void {
    const o = this.origem();
    if (!o) { return; }
    this.complementando.set(true);
    const req = { tema: this.formComplementar.tema || null, professorId: this.formComplementar.professorId ?? null };
    this.api.complementarAula(o.id, req).subscribe({
      next: (r) => {
        const msg = r.aulasMovidas > 0
          ? `Aula complementar criada; ${r.aulasMovidas} aula(s) movida(s) +7 dias.`
          : 'Aula complementar criada.';
        this.toast.sucesso(msg);
        this.complementando.set(false); this.fecharComplementar(); this.carregar();
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao desdobrar a aula.'); this.complementando.set(false); },
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
