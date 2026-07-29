import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { Aniversariante, MinhaFrequenciaResponse } from '../../core/models';

@Component({
  selector: 'app-minha-frequencia',
  standalone: true,
  imports: [DatePipe],
  styles: [`
    .motivo { font-size: .8rem; color: var(--cinza-texto); font-style: italic; }
    .aniversarios { border-left: 4px solid #d69e2e; }
    .aniv-item { display: flex; align-items: center; gap: .75rem; padding: .55rem 0; border-top: 1px solid var(--borda, #eee); flex-wrap: wrap; }
    .aniv-item:first-of-type { border-top: 0; }
    .aniv-nome { font-weight: 600; }
    .aniv-data { color: var(--cinza-texto); font-size: .85rem; }
    .aniv-hoje { background: #faf089; color: #744210; border-radius: 999px; padding: .1rem .5rem; font-size: .75rem; font-weight: 600; }
    .btn-zap { margin-left: auto; display: inline-flex; align-items: center; gap: .35rem; background: #25d366; color: #fff; text-decoration: none; padding: .35rem .7rem; border-radius: 6px; font-size: .85rem; font-weight: 600; }
    .btn-zap:hover { background: #1ebe5b; }
  `],
  template: `
    <div style="margin-bottom:1.25rem">
      <h2>Minha frequência</h2>
      <p class="muted">Olá, {{ auth.username() }} — aqui está o seu histórico de presença na EBD.</p>
    </div>

    @if (aniversariantes().length) {
      <div class="card aniversarios" style="margin-bottom:1.5rem">
        <h3 style="margin-top:0">🎉 Aniversariantes</h3>
        <p class="muted" style="margin-top:-.4rem">Quem faz aniversário hoje e nos próximos dias. Que tal mandar os parabéns?</p>
        @for (a of aniversariantes(); track a.id) {
          <div class="aniv-item">
            <span class="aniv-nome">{{ a.nome }}</span>
            @if (a.hoje) { <span class="aniv-hoje">Hoje 🎂</span> }
            @else { <span class="aniv-data">{{ a.dia }}/{{ a.mes }}</span> }
            @if (a.whatsapp) {
              <a class="btn-zap" [href]="linkWhatsapp(a)" target="_blank" rel="noopener">💬 Parabenizar no WhatsApp</a>
            }
          </div>
        }
      </div>
    }

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
            Faltou? Fale com o <strong>professor</strong>: só ele pode registrar a justificativa. Uma falta
            justificada passa a valer 30% dos pontos de uma presença no ranking.
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
                        } @else {
                          <span class="muted">Sem justificativa</span>
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
  `,
})
export class MinhaFrequenciaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  dados = signal<MinhaFrequenciaResponse | null>(null);
  aniversariantes = signal<Aniversariante[]>([]);
  carregando = signal(true);

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
    // Falha silenciosa: se não carregar, apenas não mostra o card de aniversariantes.
    this.api.meusAniversariantes().subscribe({
      next: (lista) => this.aniversariantes.set(lista),
      error: () => this.aniversariantes.set([]),
    });
  }

  linkWhatsapp(a: Aniversariante): string {
    const primeiroNome = a.nome.trim().split(/\s+/)[0];
    const msg = `Feliz aniversário, ${primeiroNome}! 🎉🎂 Que Deus te abençoe muito. Um abraço da EBD ICE Samambaia! 🙏`;
    return `https://wa.me/${a.whatsapp}?text=${encodeURIComponent(msg)}`;
  }
}
