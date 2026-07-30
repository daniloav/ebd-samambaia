import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { BoletimResponse } from '../../core/models';
import { BoletimViewComponent } from '../boletim/boletim-view.component';

/** Boletim do próprio aluno logado. */
@Component({
  selector: 'app-meu-boletim',
  standalone: true,
  imports: [FormsModule, BoletimViewComponent],
  styles: [`
    .filtros { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .filtros .form-group { margin: 0; }
  `],
  template: `
    <h2>Meu boletim</h2>
    <p class="muted">Seu desempenho no trimestre — notas e frequência.</p>

    <div class="card" style="margin-bottom:1.5rem">
      <div class="filtros">
        <div class="form-group">
          <label>Ano</label>
          <input aria-label="Ano" type="number" min="2000" max="2100" [(ngModel)]="ano" style="width:100px" />
        </div>
        <div class="form-group">
          <label>Trimestre</label>
          <select aria-label="Trimestre" [(ngModel)]="trimestre">
            <option [ngValue]="1">1º (Jan-Mar)</option>
            <option [ngValue]="2">2º (Abr-Jun)</option>
            <option [ngValue]="3">3º (Jul-Set)</option>
            <option [ngValue]="4">4º (Out-Dez)</option>
          </select>
        </div>
        <button class="btn btn-verde" (click)="gerar()" [disabled]="carregando()">
          {{ carregando() ? 'Carregando...' : 'Ver boletim' }}
        </button>
      </div>
    </div>

    @if (boletim(); as b) {
      <app-boletim-view [b]="b" />
    }
  `,
})
export class MeuBoletimComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  ano = new Date().getFullYear();
  trimestre = Math.floor(new Date().getMonth() / 3) + 1;
  boletim = signal<BoletimResponse | null>(null);
  carregando = signal(false);

  constructor() {
    this.gerar();
  }

  gerar(): void {
    this.carregando.set(true);
    this.api.meuBoletim(this.ano, this.trimestre).subscribe({
      next: (b) => { this.boletim.set(b); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao gerar o boletim.'); this.carregando.set(false); },
    });
  }
}
