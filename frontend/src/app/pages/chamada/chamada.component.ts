import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../core/confirm.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { Aula, PresencaItem, Professor, Visitante, VisitanteRequest } from '../../core/models';

@Component({
  selector: 'app-chamada',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .barra { display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end; margin-bottom: 1.25rem; }
    .barra .form-group { margin: 0; }
    .chk { text-align: center; cursor: pointer; min-width: 44px; }
    .chk input { width: 22px; height: 22px; cursor: pointer; accent-color: var(--verde); vertical-align: middle; }
    tbody tr td { padding-top: .55rem; padding-bottom: .55rem; }
    @media (max-width: 600px) {
      .chk input { width: 26px; height: 26px; }
      /* modo cartão: nome do aluno = cabeçalho; itens = linhas rotuladas com toque grande */
      .chamada-cards .nome-col { background: var(--superficie-2); font-weight: 700; font-size: 1rem;
        justify-content: flex-start; padding: .6rem .8rem; }
      .chamada-cards td.chk { justify-content: space-between; text-align: left; min-height: 46px; }
      .chamada-cards td.chk::before { text-transform: none; font-size: .9rem; font-weight: 600; color: var(--texto); }
    }
    .nome-col { min-width: 180px; }
    .resumo { display: flex; gap: 1.5rem; flex-wrap: wrap; margin-top: 1rem; }
    .resumo span { font-size: .85rem; color: var(--cinza-texto); }
    .resumo b { color: var(--azul); }
    .nova-aula { display: flex; gap: .6rem; align-items: flex-end; flex-wrap: wrap; }
    .just-btn { white-space: nowrap; }
    .just-motivo { font-size: .78rem; color: var(--cinza-texto); font-style: italic; margin-top: .2rem; }
    .modal-fundo { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex;
      align-items: center; justify-content: center; z-index: 50; padding: 1rem; }
    .modal-caixa { background: var(--superficie); border-radius: 12px; padding: 1.25rem;
      width: 100%; max-width: 460px; box-shadow: 0 10px 40px rgba(0,0,0,.25); }
    .modal-caixa textarea { width: 100%; min-height: 90px; resize: vertical; }
    .modal-acoes { display: flex; gap: .6rem; justify-content: flex-end; margin-top: 1rem; }
  `],
  template: `
    <h2>Fazer chamada</h2>
    <p class="muted">Selecione a aula e marque a presença e os itens de cada aluno.</p>

    <div class="card">
      <div class="barra">
        <div class="form-group" style="flex:1;min-width:220px">
          <label>Aula</label>
          <select [(ngModel)]="aulaSelecionadaId" (ngModelChange)="aoTrocarAula($event)">
            <option [ngValue]="null" disabled>Selecione uma aula...</option>
            @for (a of aulas(); track a.id) {
              <option [ngValue]="a.id">{{ a.data | date:'dd/MM/yyyy' }}{{ a.tema ? ' — ' + a.tema : '' }}</option>
            }
          </select>
        </div>
        <button class="btn btn-outline" (click)="mostrarNovaAula.set(!mostrarNovaAula())">
          {{ mostrarNovaAula() ? 'Cancelar' : '+ Nova aula' }}
        </button>
      </div>

      @if (mostrarNovaAula()) {
        <div class="nova-aula" style="margin-bottom:1rem">
          <div class="form-group"><label>Data *</label><input type="date" [(ngModel)]="novaData" /></div>
          <div class="form-group" style="flex:1;min-width:200px">
            <label>Tema (opcional)</label><input type="text" [(ngModel)]="novoTema" maxlength="200" /></div>
          <div class="form-group" style="min-width:190px">
            <label>Professor da aula</label>
            <select [(ngModel)]="novoProfessorId">
              <option [ngValue]="null">— sem professor —</option>
              @for (pr of professores(); track pr.id) { <option [ngValue]="pr.id">{{ pr.nome }}</option> }
            </select>
          </div>
          <button class="btn btn-verde" (click)="criarAula()" [disabled]="salvandoAula()">Criar aula</button>
        </div>
      }

      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando chamada...</div>
      } @else if (aulaSelecionadaId) {
        <div class="flex-between" style="margin-bottom:.5rem">
          <strong>{{ itens().length }} aluno(s)</strong>
          <button class="btn btn-outline btn-sm" (click)="marcarTodosPresentes()">Marcar todos presentes</button>
        </div>
        <div class="tabela-scroll">
          <table class="tabela tabela-cards chamada-cards">
            <thead>
              <tr>
                <th class="nome-col">Aluno</th>
                <th class="chk">Presente</th>
                <th class="chk">Bíblia</th>
                <th class="chk">Revista</th>
                <th class="chk">Estudou a lição</th>
                <th class="chk">Falta justificada</th>
              </tr>
            </thead>
            <tbody>
              @for (i of itens(); track i.alunoId) {
                <tr [style.opacity]="i.professorDaAula ? .55 : 1">
                  <td class="nome-col">{{ i.alunoNome }}
                    @if (i.professorDaAula) {
                      <span style="display:inline-block;margin-left:.4rem;font-size:.72rem;font-weight:700;padding:.1rem .5rem;border-radius:999px;background:#e7eefb;color:#1e40af">👩‍🏫 Dando aula</span>
                    }
                  </td>
                  <td class="chk" data-label="Presente" (click)="!i.professorDaAula && (i.presente = !i.presente)">
                    <input type="checkbox" [(ngModel)]="i.presente" [disabled]="!!i.professorDaAula" (click)="$event.stopPropagation()" /></td>
                  <td class="chk" data-label="Trouxe a Bíblia" (click)="!i.professorDaAula && (i.trouxeBiblia = !i.trouxeBiblia)">
                    <input type="checkbox" [(ngModel)]="i.trouxeBiblia" [disabled]="!!i.professorDaAula" (click)="$event.stopPropagation()" /></td>
                  <td class="chk" data-label="Trouxe a revista" (click)="!i.professorDaAula && (i.trouxeRevista = !i.trouxeRevista)">
                    <input type="checkbox" [(ngModel)]="i.trouxeRevista" [disabled]="!!i.professorDaAula" (click)="$event.stopPropagation()" /></td>
                  <td class="chk" data-label="Estudou a lição" (click)="!i.professorDaAula && (i.estudouLicao = !i.estudouLicao)">
                    <input type="checkbox" [(ngModel)]="i.estudouLicao" [disabled]="!!i.professorDaAula" (click)="$event.stopPropagation()" /></td>
                  <td class="chk" data-label="Falta justificada">
                    @if (i.professorDaAula || i.presente) { <span class="muted">—</span> }
                    @else if (i.justificada) {
                      <span class="badge" style="background:#faf089;color:#744210" [title]="i.justificativaMotivo || ''">Justificada</span>
                      @if (i.justificativaMotivo) { <div class="just-motivo" [title]="i.justificativaMotivo">"{{ i.justificativaMotivo }}"</div> }
                      <div style="margin-top:.3rem">
                        <button class="btn btn-outline btn-sm just-btn" (click)="abrirJustificar(i)">Editar</button>
                        <button class="btn btn-outline btn-sm just-btn" style="margin-left:.3rem" (click)="removerJustificativa(i)">Remover</button>
                      </div>
                    } @else {
                      <button class="btn btn-outline btn-sm just-btn" (click)="abrirJustificar(i)">Justificar falta</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="resumo">
          <span>Presentes: <b>{{ contar('presente') }}</b></span>
          <span>Bíblias: <b>{{ contar('trouxeBiblia') }}</b></span>
          <span>Revistas: <b>{{ contar('trouxeRevista') }}</b></span>
          <span>Estudaram: <b>{{ contar('estudouLicao') }}</b></span>
          <span>Visitantes (cadastrados): <b>{{ visitantes().length }}</b></span>
        </div>

        <div class="mt">
          <button class="btn btn-verde" (click)="salvar()" [disabled]="salvando()">
            {{ salvando() ? 'Salvando...' : '💾 Salvar chamada' }}
          </button>
        </div>
      } @else {
        <p class="muted text-center">Selecione ou crie uma aula para iniciar a chamada.</p>
      }
    </div>

    @if (aulaSelecionadaId && !carregando()) {
      <div class="card" style="margin-top:1.5rem">
        <h3 style="margin-top:0">Visitantes desta aula</h3>
        <p class="muted" style="margin-top:-.4rem">
          Ao cadastrar, o visitante recebe um e-mail de boas-vindas e os professores são avisados.
        </p>
        <div class="nova-aula" style="margin-bottom:1rem">
          <div class="form-group"><label>Nome *</label>
            <input type="text" [(ngModel)]="novoVisitante.nome" maxlength="120" /></div>
          <div class="form-group"><label>E-mail</label>
            <input type="email" [(ngModel)]="novoVisitante.email" maxlength="150" /></div>
          <div class="form-group"><label>Telefone</label>
            <input type="text" [(ngModel)]="novoVisitante.telefone" maxlength="20" /></div>
          <div class="form-group" style="min-width:180px"><label>Trazido por <small style="opacity:.6">(só presentes)</small></label>
            <select [(ngModel)]="novoVisitante.trazidoPorAlunoId">
              <option [ngValue]="null">— não informado —</option>
              @for (i of itens(); track i.alunoId) { @if (i.presente) { <option [ngValue]="i.alunoId">{{ i.alunoNome }}</option> } }
            </select>
          </div>
          <button class="btn btn-verde" (click)="adicionarVisitante()" [disabled]="salvandoVisitante()">
            {{ salvandoVisitante() ? 'Enviando...' : '+ Adicionar' }}
          </button>
        </div>

        @if (visitantes().length === 0) {
          <p class="muted text-center">Nenhum visitante cadastrado nesta aula.</p>
        } @else {
          <div class="tabela-scroll">
            <table class="tabela">
              <thead><tr><th>Nome</th><th>Contato</th><th>Trazido por</th><th style="width:90px"></th></tr></thead>
              <tbody>
                @for (v of visitantes(); track v.id) {
                  <tr>
                    <td>{{ v.nome }}</td>
                    <td>{{ contato(v) }}</td>
                    <td>{{ v.trazidoPorNome || '—' }}</td>
                    <td><button class="btn btn-perigo btn-sm" (click)="removerVisitante(v)">Remover</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    }

    @if (justificando(); as j) {
      <div class="modal-fundo" (click)="fecharJustificar()">
        <div class="modal-caixa" (click)="$event.stopPropagation()">
          <h3 style="margin-top:0">Justificar falta</h3>
          <p class="muted" style="margin-top:-.3rem">{{ j.alunoNome }}</p>
          <div class="form-group">
            <label>Motivo *</label>
            <textarea [(ngModel)]="motivoJustificativa" maxlength="300"
              placeholder="Ex.: estava doente, viagem de trabalho..."></textarea>
          </div>
          <p class="muted" style="font-size:.78rem;margin-top:-.4rem">
            A justificativa é registrada ao <strong>salvar a chamada</strong>.
          </p>
          <div class="modal-acoes">
            <button class="btn btn-outline" (click)="fecharJustificar()">Cancelar</button>
            <button class="btn btn-verde" (click)="confirmarJustificar()">Aplicar</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ChamadaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  private classeCtx = inject(ClasseContextService);

  aulas = signal<Aula[]>([]);
  itens = signal<PresencaItem[]>([]);
  aulaSelecionadaId: number | null = null;
  carregando = signal(false);
  salvando = signal(false);

  mostrarNovaAula = signal(false);
  professores = signal<Professor[]>([]);
  novaData = '';
  novoTema = '';
  novoProfessorId: number | null = null;
  salvandoAula = signal(false);

  visitantes = signal<Visitante[]>([]);
  salvandoVisitante = signal(false);
  novoVisitante: VisitanteRequest = this.visitanteVazio();

  justificando = signal<PresencaItem | null>(null);
  motivoJustificativa = '';

  constructor() {
    effect(() => {
      this.classeCtx.selecionadaId();
      this.aulaSelecionadaId = null;
      this.itens.set([]);
      this.visitantes.set([]);
      this.carregarAulas();
      this.carregarProfessores();
    }, { allowSignalWrites: true });
  }

  private carregarProfessores(): void {
    const cid = this.classeCtx.selecionadaId();
    if (!cid) { this.professores.set([]); return; }
    this.api.listarProfessores(cid).subscribe({ next: (l) => this.professores.set(l), error: () => this.professores.set([]) });
  }

  private visitanteVazio(): VisitanteRequest {
    return { nome: '', email: '', telefone: '', trazidoPorAlunoId: null };
  }

  carregarAulas(selecionar?: number): void {
    this.api.listarAulas(this.classeCtx.selecionadaId()).subscribe({
      next: (l) => {
        this.aulas.set(l);
        if (selecionar) {
          this.aulaSelecionadaId = selecionar;
          this.aoTrocarAula(selecionar);
        }
      },
      error: () => this.toast.erro('Falha ao carregar aulas.'),
    });
  }

  aoTrocarAula(id: number | null): void {
    if (!id) return;
    this.carregando.set(true);
    this.visitantes.set([]);
    this.api.obterChamada(id).subscribe({
      next: (r) => { this.itens.set(r.itens.map((i) => ({ ...i }))); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar a chamada.'); this.carregando.set(false); },
    });
    this.carregarVisitantes(id);
  }

  carregarVisitantes(aulaId: number): void {
    this.api.listarVisitantes(aulaId).subscribe({
      next: (l) => this.visitantes.set(l),
      error: () => {},
    });
  }

  criarAula(): void {
    if (!this.novaData) { this.toast.erro('Informe a data da aula.'); return; }
    this.salvandoAula.set(true);
    this.api.criarAula({ classeId: this.classeCtx.selecionadaId() ?? undefined, data: this.novaData, tema: this.novoTema || null, professorId: this.novoProfessorId }).subscribe({
      next: (a) => {
        this.toast.sucesso('Aula criada!');
        this.salvandoAula.set(false);
        this.mostrarNovaAula.set(false);
        this.novaData = ''; this.novoTema = ''; this.novoProfessorId = null;
        this.carregarAulas(a.id);
      },
      error: (e) => {
        this.toast.erro(e?.error?.message || 'Erro ao criar aula.');
        this.salvandoAula.set(false);
      },
    });
  }

  marcarTodosPresentes(): void {
    this.itens.update((lista) => lista.map((i) => (i.professorDaAula ? i : { ...i, presente: true })));
  }

  abrirJustificar(i: PresencaItem): void {
    this.motivoJustificativa = i.justificativaMotivo || '';
    this.justificando.set(i);
  }

  fecharJustificar(): void {
    this.justificando.set(null);
    this.motivoJustificativa = '';
  }

  /** Aplica a justificativa no item (só persiste ao salvar a chamada). */
  confirmarJustificar(): void {
    const item = this.justificando();
    if (!item) return;
    const motivo = this.motivoJustificativa.trim();
    if (!motivo) { this.toast.erro('Informe o motivo da falta.'); return; }
    this.itens.update((lista) => lista.map((i) =>
      i.alunoId === item.alunoId ? { ...i, justificada: true, justificativaMotivo: motivo } : i));
    this.fecharJustificar();
  }

  removerJustificativa(i: PresencaItem): void {
    this.itens.update((lista) => lista.map((x) =>
      x.alunoId === i.alunoId ? { ...x, justificada: false, justificativaMotivo: null } : x));
  }

  contar(campo: keyof PresencaItem): number {
    return this.itens().filter((i) => i[campo] === true).length;
  }

  /** Só atribui "trazido por" se o aluno estiver presente (não faz sentido ausente trazer visitante). */
  private trazidoPorValido(): number | null {
    const id = this.novoVisitante.trazidoPorAlunoId ?? null;
    if (id == null) return null;
    const aluno = this.itens().find((i) => i.alunoId === id);
    return aluno && aluno.presente ? id : null;
  }

  contato(v: Visitante): string {
    const partes = [v.email, v.telefone].filter((x) => !!x);
    return partes.length ? partes.join(' · ') : '—';
  }

  adicionarVisitante(): void {
    if (!this.aulaSelecionadaId) return;
    if (!this.novoVisitante.nome?.trim()) { this.toast.erro('Informe o nome do visitante.'); return; }
    this.salvandoVisitante.set(true);
    const payload: VisitanteRequest = {
      nome: this.novoVisitante.nome.trim(),
      email: this.novoVisitante.email || null,
      telefone: this.novoVisitante.telefone || null,
      trazidoPorAlunoId: this.trazidoPorValido(),
    };
    this.api.adicionarVisitante(this.aulaSelecionadaId, payload).subscribe({
      next: (r) => {
        this.toast.sucesso('Visitante cadastrado! Boas-vindas e aviso enviados.');
        if (r?.alerta) { this.toast.sucesso(r.alerta); }
        this.novoVisitante = this.visitanteVazio();
        this.salvandoVisitante.set(false);
        this.carregarVisitantes(this.aulaSelecionadaId!);
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao cadastrar visitante.'); this.salvandoVisitante.set(false); },
    });
  }

  async removerVisitante(v: Visitante): Promise<void> {
    if (!(await this.confirm.pedir({ titulo: 'Remover visitante', mensagem: `Remover o visitante "${v.nome}"?`, confirmar: 'Remover', perigo: true }))) { return; }
    this.api.removerVisitante(v.id).subscribe({
      next: () => { this.toast.sucesso('Visitante removido.'); if (this.aulaSelecionadaId) this.carregarVisitantes(this.aulaSelecionadaId); },
      error: (e) => this.toast.erro(e?.error?.message || 'Erro ao remover visitante.'),
    });
  }

  salvar(): void {
    if (!this.aulaSelecionadaId) return;
    this.salvando.set(true);
    const payload = this.itens().map((i) => ({
      alunoId: i.alunoId,
      presente: i.presente,
      trouxeBiblia: i.trouxeBiblia,
      trouxeRevista: i.trouxeRevista,
      estudouLicao: i.estudouLicao,
      justificada: !i.presente && !!i.justificada,
      justificativaMotivo: !i.presente && i.justificada ? (i.justificativaMotivo ?? null) : null,
    }));
    this.api.salvarChamada(this.aulaSelecionadaId, payload).subscribe({
      next: (r) => {
        const n = r?.emailsEnviados ?? 0;
        this.toast.sucesso(n > 0 ? `Chamada salva! ${n} e-mail(s) de notificação enviado(s).` : 'Chamada salva com sucesso!');
        (r?.alertas ?? []).forEach((msg) => this.toast.sucesso(msg));
        // Recarrega a lista: um aluno pode ter sido inativado (some da chamada).
        if ((r?.alertas ?? []).length && this.aulaSelecionadaId) { this.aoTrocarAula(this.aulaSelecionadaId); }
        this.salvando.set(false);
      },
      error: () => { this.toast.erro('Erro ao salvar a chamada.'); this.salvando.set(false); },
    });
  }
}
