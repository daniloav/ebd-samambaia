import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { Campanha, CampanhaRequest } from '../../core/models';

@Component({
  selector: 'app-campanhas',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="flex-between" style="margin-bottom:1.25rem">
      <div>
        <h2>Campanhas</h2>
        <p class="muted">Envie um e-mail em massa aos alunos que optaram por receber avisos.</p>
      </div>
    </div>

    <div class="card" style="margin-bottom:1.5rem">
      <h3 style="margin-top:0">Nova campanha</h3>
      <div class="form-group">
        <label>Público-alvo</label>
        <select [(ngModel)]="classeId">
          <option [ngValue]="null">Todas as turmas</option>
          @for (c of classeCtx.classes(); track c.id) {
            <option [ngValue]="c.id">{{ c.nome }}</option>
          }
        </select>
      </div>
      <div class="form-group">
        <label>Título / assunto *</label>
        <input type="text" [(ngModel)]="form.titulo" maxlength="150" placeholder="Ex.: Culto especial neste domingo" />
      </div>
      <div class="form-group">
        <label>Mensagem *</label>
        <textarea [(ngModel)]="form.mensagem" rows="6" maxlength="5000"
                  placeholder="Escreva a mensagem que será enviada aos alunos..."></textarea>
      </div>
      <div class="flex-between" style="align-items:center">
        <span class="muted" style="font-size:.85rem">
          Só recebem alunos <b>ativos</b>, com <b>e-mail</b> e <b>opt-in</b> marcados.
        </span>
        <button class="btn btn-verde" (click)="enviar()" [disabled]="enviando()">
          {{ enviando() ? 'Enviando...' : 'Enviar campanha' }}
        </button>
      </div>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Histórico</h3>
      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (campanhas().length === 0) {
        <p class="muted text-center">Nenhuma campanha enviada ainda.</p>
      } @else {
        <div class="tabela-scroll">
          <table class="tabela">
            <thead>
              <tr>
                <th style="width:150px">Data</th>
                <th>Título</th>
                <th>Público</th>
                <th style="width:100px">Enviados</th>
                <th>Por</th>
              </tr>
            </thead>
            <tbody>
              @for (c of campanhas(); track c.id) {
                <tr>
                  <td>{{ c.dataEnvio | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td>{{ c.titulo }}</td>
                  <td>{{ c.classeNome || 'Todas as turmas' }}</td>
                  <td><span class="badge badge-verde">{{ c.totalEnviados }}</span></td>
                  <td>{{ c.criadoPor || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class CampanhasComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  classeCtx = inject(ClasseContextService);

  campanhas = signal<Campanha[]>([]);
  carregando = signal(true);
  enviando = signal(false);
  classeId: number | null = this.classeCtx.selecionadaId();
  form: CampanhaRequest = { titulo: '', mensagem: '' };

  constructor() {
    this.classeCtx.carregar();
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarCampanhas().subscribe({
      next: (l) => { this.campanhas.set(l); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar campanhas.'); this.carregando.set(false); },
    });
  }

  enviar(): void {
    if (!this.form.titulo?.trim()) { this.toast.erro('Informe o título.'); return; }
    if (!this.form.mensagem?.trim()) { this.toast.erro('Informe a mensagem.'); return; }
    const alvo = this.classeId
      ? (this.classeCtx.classes().find((c) => c.id === this.classeId)?.nome ?? 'a turma selecionada')
      : 'TODAS as turmas';
    if (!confirm(`Enviar esta campanha para os alunos com opt-in de ${alvo}?`)) return;

    this.enviando.set(true);
    const payload: CampanhaRequest = {
      titulo: this.form.titulo.trim(),
      mensagem: this.form.mensagem,
      classeId: this.classeId,
    };
    this.api.criarCampanha(payload).subscribe({
      next: (c) => {
        this.toast.sucesso(`Campanha enviada para ${c.totalEnviados} aluno(s).`);
        this.form = { titulo: '', mensagem: '' };
        this.enviando.set(false);
        this.carregar();
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao enviar campanha.'); this.enviando.set(false); },
    });
  }
}
