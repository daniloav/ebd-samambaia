import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { Aula, PresencaItem } from '../../core/models';

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
          <span>Visitantes: <b>{{ contar('trouxeVisitante') }}</b></span>
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
  `,
})
export class ChamadaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  aulas = signal<Aula[]>([]);
  itens = signal<PresencaItem[]>([]);
  aulaSelecionadaId: number | null = null;
  carregando = signal(false);
  salvando = signal(false);

  mostrarNovaAula = signal(false);
  novaData = '';
  novoTema = '';
  salvandoAula = signal(false);

  constructor() {
    this.carregarAulas();
  }

  carregarAulas(selecionar?: number): void {
    this.api.listarAulas().subscribe({
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
    this.api.obterChamada(id).subscribe({
      next: (r) => { this.itens.set(r.itens.map((i) => ({ ...i }))); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar a chamada.'); this.carregando.set(false); },
    });
  }

  criarAula(): void {
    if (!this.novaData) { this.toast.erro('Informe a data da aula.'); return; }
    this.salvandoAula.set(true);
    this.api.criarAula({ data: this.novaData, tema: this.novoTema || null }).subscribe({
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
