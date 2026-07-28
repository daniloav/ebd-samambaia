import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { MinhaFrequenciaItem, MinhaFrequenciaResponse } from '../../core/models';

@Component({
  selector: 'app-minha-frequencia',
  standalone: true,
  imports: [DatePipe, FormsModule],
  styles: [`
    .modal-fundo { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex;
      align-items: center; justify-content: center; z-index: 50; padding: 1rem; }
    .modal-caixa { background: var(--superficie); border-radius: 12px; padding: 1.25rem;
      width: 100%; max-width: 460px; box-shadow: 0 10px 40px rgba(0,0,0,.25); }
    .modal-caixa textarea { width: 100%; min-height: 90px; resize: vertical; }
    .modal-acoes { display: flex; gap: .6rem; justify-content: flex-end; margin-top: 1rem; }
    .motivo { font-size: .8rem; color: var(--cinza-texto); font-style: italic; }
  `],
  template: `
    <div style="margin-bottom:1.25rem">
      <h2>Minha frequência</h2>
      <p class="muted">Olá, {{ auth.username() }} — aqui está o seu histórico de presença na EBD.</p>
    </div>

    @if (carregando()) {
      <div class="card"><div class="spinner-wrap muted">Carregando...</div></div>
    } @else {
      @if (dados(); as d) {
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:1rem;margin-bottom:1.5rem">
          <div class="card text-center"><div class="muted">Aulas</div><strong style="font-size:1.6rem">{{ d.totalAulas }}</strong></div>
          <div class="card text-center"><div class="muted">Presenças</div><strong style="font-size:1.6rem;color:#2f855a">{{ d.presencas }}</strong></div>
          <div class="card text-center"><div class="muted">Faltas</div><strong style="font-size:1.6rem;color:#c53030">{{ d.faltas }}</strong></div>
          <div class="card text-center"><div class="muted">Justificadas</div><strong style="font-size:1.6rem;color:#b7791f">{{ d.faltasJustificadas }}</strong></div>
          <div class="card text-center"><div class="muted">Presença</div><strong style="font-size:1.6rem">{{ d.percentualPresenca }}%</strong></div>
        </div>

        <div class="card">
          <h3 style="margin-top:0">Aulas</h3>
          <p class="muted" style="margin-top:-.4rem">
            Faltou? Você pode <strong>justificar</strong> a falta — ela passa a valer 30% dos pontos de uma presença no ranking.
          </p>
          @if (d.itens.length === 0) {
            <p class="muted text-center">Você ainda não tem registros de chamada.</p>
          } @else {
            <div class="tabela-scroll">
              <table class="tabela">
                <thead>
                  <tr>
                    <th style="width:120px">Data</th><th>Tema</th>
                    <th style="width:110px">Presença</th><th>Bíblia</th><th>Revista</th><th>Lição</th>
                    <th style="width:200px">Justificativa</th>
                  </tr>
                </thead>
                <tbody>
                  @for (i of d.itens; track i.aulaId) {
                    <tr>
                      <td>{{ i.data | date:'dd/MM/yyyy' }}</td>
                      <td>{{ i.tema || '—' }}</td>
                      <td>
                        @if (i.presente) { <span class="badge badge-verde">Presente</span> }
                        @else if (i.justificada) { <span class="badge badge-cinza">Ausente</span> }
                        @else { <span class="badge badge-cinza">Ausente</span> }
                      </td>
                      <td>{{ i.trouxeBiblia ? 'Sim' : '—' }}</td>
                      <td>{{ i.trouxeRevista ? 'Sim' : '—' }}</td>
                      <td>{{ i.estudouLicao ? 'Sim' : '—' }}</td>
                      <td>
                        @if (i.presente) { <span class="muted">—</span> }
                        @else if (i.justificada) {
                          <span class="badge" style="background:#faf089;color:#744210">Justificada</span>
                          @if (i.justificativaMotivo) { <div class="motivo" [title]="i.justificativaMotivo">"{{ i.justificativaMotivo }}"</div> }
                          <button class="btn btn-outline btn-sm" style="margin-top:.35rem" (click)="removerJustificativa(i)">Remover</button>
                        } @else if (i.podeJustificar) {
                          <button class="btn btn-outline btn-sm" (click)="abrirJustificar(i)">Justificar falta</button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      } @else {
        <div class="card"><p class="muted text-center">Não foi possível carregar sua frequência.</p></div>
      }
    }

    @if (justificando(); as j) {
      <div class="modal-fundo" (click)="fecharJustificar()">
        <div class="modal-caixa" (click)="$event.stopPropagation()">
          <h3 style="margin-top:0">Justificar falta</h3>
          <p class="muted" style="margin-top:-.3rem">Aula de {{ j.data | date:'dd/MM/yyyy' }}{{ j.tema ? ' — ' + j.tema : '' }}</p>
          <div class="form-group">
            <label>Motivo *</label>
            <textarea [(ngModel)]="motivo" maxlength="300" placeholder="Ex.: estava doente, viagem de trabalho..."></textarea>
          </div>
          <div class="modal-acoes">
            <button class="btn btn-outline" (click)="fecharJustificar()" [disabled]="salvando()">Cancelar</button>
            <button class="btn btn-verde" (click)="confirmarJustificar()" [disabled]="salvando()">
              {{ salvando() ? 'Salvando...' : 'Justificar' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class MinhaFrequenciaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  dados = signal<MinhaFrequenciaResponse | null>(null);
  carregando = signal(true);

  justificando = signal<MinhaFrequenciaItem | null>(null);
  motivo = '';
  salvando = signal(false);

  constructor() {
    this.carregar();
  }

  private carregar(): void {
    this.carregando.set(true);
    this.api.minhaFrequencia().subscribe({
      next: (d) => { this.dados.set(d); this.carregando.set(false); },
      error: (e) => {
        this.toast.erro(e?.error?.message || 'Não foi possível carregar sua frequência.');
        this.carregando.set(false);
      },
    });
  }

  abrirJustificar(i: MinhaFrequenciaItem): void {
    this.motivo = i.justificativaMotivo || '';
    this.justificando.set(i);
  }

  fecharJustificar(): void {
    this.justificando.set(null);
    this.motivo = '';
  }

  confirmarJustificar(): void {
    const item = this.justificando();
    if (!item) return;
    if (!this.motivo.trim()) { this.toast.erro('Informe o motivo da falta.'); return; }
    this.salvando.set(true);
    this.api.justificarFalta(item.aulaId, this.motivo.trim()).subscribe({
      next: () => {
        this.toast.sucesso('Falta justificada!');
        this.salvando.set(false);
        this.fecharJustificar();
        this.carregar();
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao justificar a falta.'); this.salvando.set(false); },
    });
  }

  removerJustificativa(i: MinhaFrequenciaItem): void {
    this.api.removerJustificativa(i.aulaId).subscribe({
      next: () => { this.toast.sucesso('Justificativa removida.'); this.carregar(); },
      error: (e) => this.toast.erro(e?.error?.message || 'Erro ao remover a justificativa.'),
    });
  }
}
