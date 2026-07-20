import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { MinhaFrequenciaResponse } from '../../core/models';

@Component({
  selector: 'app-minha-frequencia',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div style="margin-bottom:1.25rem">
      <h2>Minha frequência</h2>
      <p class="muted">Olá, {{ auth.username() }} — aqui está o seu histórico de presença na EBD.</p>
    </div>

    @if (carregando()) {
      <div class="card"><div class="spinner-wrap muted">Carregando...</div></div>
    } @else {
      @if (dados(); as d) {
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:1rem;margin-bottom:1.5rem">
          <div class="card text-center"><div class="muted">Aulas</div><strong style="font-size:1.6rem">{{ d.totalAulas }}</strong></div>
          <div class="card text-center"><div class="muted">Presenças</div><strong style="font-size:1.6rem;color:#2f855a">{{ d.presencas }}</strong></div>
          <div class="card text-center"><div class="muted">Faltas</div><strong style="font-size:1.6rem;color:#c53030">{{ d.faltas }}</strong></div>
          <div class="card text-center"><div class="muted">Presença</div><strong style="font-size:1.6rem">{{ d.percentualPresenca }}%</strong></div>
        </div>

        <div class="card">
          <h3 style="margin-top:0">Aulas</h3>
          @if (d.itens.length === 0) {
            <p class="muted text-center">Você ainda não tem registros de chamada.</p>
          } @else {
            <div class="tabela-scroll">
              <table class="tabela">
                <thead>
                  <tr>
                    <th style="width:120px">Data</th><th>Tema</th>
                    <th style="width:110px">Presença</th><th>Bíblia</th><th>Revista</th><th>Lição</th>
                  </tr>
                </thead>
                <tbody>
                  @for (i of d.itens; track $index) {
                    <tr>
                      <td>{{ i.data | date:'dd/MM/yyyy' }}</td>
                      <td>{{ i.tema || '—' }}</td>
                      <td>
                        @if (i.presente) { <span class="badge badge-verde">Presente</span> }
                        @else { <span class="badge badge-cinza">Ausente</span> }
                      </td>
                      <td>{{ i.trouxeBiblia ? 'Sim' : '—' }}</td>
                      <td>{{ i.trouxeRevista ? 'Sim' : '—' }}</td>
                      <td>{{ i.estudouLicao ? 'Sim' : '—' }}</td>
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
  `,
})
export class MinhaFrequenciaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  dados = signal<MinhaFrequenciaResponse | null>(null);
  carregando = signal(true);

  constructor() {
    this.api.minhaFrequencia().subscribe({
      next: (d) => { this.dados.set(d); this.carregando.set(false); },
      error: (e) => {
        this.toast.erro(e?.error?.message || 'Não foi possível carregar sua frequência.');
        this.carregando.set(false);
      },
    });
  }
}
