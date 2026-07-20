import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { Aula, PresencaItem, Visitante, VisitanteRequest } from '../../core/models';

@Component({
  selector: 'app-chamada',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .barra { display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end; margin-bottom: 1.25rem; }
    .barra .form-group { margin: 0; }
    .chk { text-align: center; }
    .nome-col { min-width: 180px; }
    .resumo { display: flex; gap: 1.5rem; flex-wrap: wrap; margin-top: 1rem; }
    .resumo span { font-size: .85rem; color: var(--cinza-texto); }
    .resumo b { color: var(--azul); }
    .nova-aula { display: flex; gap: .6rem; align-items: flex-end; flex-wrap: wrap; }
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
            <label>Tema (opcional)</label><input type="text" [(ngModel)]="novoTema" maxlength="200" />
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
          <table class="tabela">
            <thead>
              <tr>
                <th class="nome-col">Aluno</th>
                <th class="chk">Presente</th>
                <th class="chk">Bíblia</th>
                <th class="chk">Revista</th>
                <th class="chk">Estudou a lição</th>
                <th class="chk">Visitante</th>
              </tr>
            </thead>
            <tbody>
              @for (i of itens(); track i.alunoId) {
                <tr>
                  <td class="nome-col">{{ i.alunoNome }}</td>
                  <td class="chk"><input type="checkbox" [(ngModel)]="i.presente" /></td>
                  <td class="chk"><input type="checkbox" [(ngModel)]="i.trouxeBiblia" /></td>
                  <td class="chk"><input type="checkbox" [(ngModel)]="i.trouxeRevista" /></td>
                  <td class="chk"><input type="checkbox" [(ngModel)]="i.estudouLicao" /></td>
                  <td class="chk"><input type="checkbox" [(ngModel)]="i.trouxeVisitante" /></td>
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
          <div class="form-group" style="min-width:180px"><label>Trazido por</label>
            <select [(ngModel)]="novoVisitante.trazidoPorAlunoId">
              <option [ngValue]="null">— não informado —</option>
              @for (i of itens(); track i.alunoId) { <option [ngValue]="i.alunoId">{{ i.alunoNome }}</option> }
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
  `,
})
export class ChamadaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private classeCtx = inject(ClasseContextService);

  aulas = signal<Aula[]>([]);
  itens = signal<PresencaItem[]>([]);
  aulaSelecionadaId: number | null = null;
  carregando = signal(false);
  salvando = signal(false);

  mostrarNovaAula = signal(false);
  novaData = '';
  novoTema = '';
  salvandoAula = signal(false);

  visitantes = signal<Visitante[]>([]);
  salvandoVisitante = signal(false);
  novoVisitante: VisitanteRequest = this.visitanteVazio();

  constructor() {
    effect(() => {
      this.classeCtx.selecionadaId();
      this.aulaSelecionadaId = null;
      this.itens.set([]);
      this.visitantes.set([]);
      this.carregarAulas();
    }, { allowSignalWrites: true });
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
    this.api.criarAula({ classeId: this.classeCtx.selecionadaId() ?? undefined, data: this.novaData, tema: this.novoTema || null }).subscribe({
      next: (a) => {
        this.toast.sucesso('Aula criada!');
        this.salvandoAula.set(false);
        this.mostrarNovaAula.set(false);
        this.novaData = ''; this.novoTema = '';
        this.carregarAulas(a.id);
      },
      error: (e) => {
        this.toast.erro(e?.error?.message || 'Erro ao criar aula.');
        this.salvandoAula.set(false);
      },
    });
  }

  marcarTodosPresentes(): void {
    this.itens.update((lista) => lista.map((i) => ({ ...i, presente: true })));
  }

  contar(campo: keyof PresencaItem): number {
    return this.itens().filter((i) => i[campo] === true).length;
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
      trazidoPorAlunoId: this.novoVisitante.trazidoPorAlunoId ?? null,
    };
    this.api.adicionarVisitante(this.aulaSelecionadaId, payload).subscribe({
      next: () => {
        this.toast.sucesso('Visitante cadastrado! Boas-vindas e aviso enviados.');
        this.novoVisitante = this.visitanteVazio();
        this.salvandoVisitante.set(false);
        this.carregarVisitantes(this.aulaSelecionadaId!);
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao cadastrar visitante.'); this.salvandoVisitante.set(false); },
    });
  }

  removerVisitante(v: Visitante): void {
    if (!confirm(`Remover o visitante "${v.nome}"?`)) return;
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
      trouxeVisitante: i.trouxeVisitante,
    }));
    this.api.salvarChamada(this.aulaSelecionadaId, payload).subscribe({
      next: () => { this.toast.sucesso('Chamada salva com sucesso!'); this.salvando.set(false); },
      error: () => { this.toast.erro('Erro ao salvar a chamada.'); this.salvando.set(false); },
    });
  }
}
