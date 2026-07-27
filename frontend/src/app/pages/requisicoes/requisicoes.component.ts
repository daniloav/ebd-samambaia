import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../core/confirm.service';
import { Requisicao, RequisicaoRequest, StatusRequisicao } from '../../core/models';

/** Módulo de requisições da tesouraria (líder abre/finaliza; tesoureiro aprova/nega). */
@Component({
  selector: 'app-requisicoes',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .barra { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1rem; }
    .barra .form-group { margin: 0; }
    .req { border: 1px solid var(--cinza-borda); border-radius: 11px; padding: .9rem 1.1rem; margin-bottom: .8rem;
      background: var(--superficie); }
    .req .topo { display: flex; align-items: center; gap: .6rem; flex-wrap: wrap; }
    .req .num { font-weight: 800; color: var(--titulo); font-variant-numeric: tabular-nums; }
    .req .val { margin-left: auto; font-weight: 800; color: var(--titulo); }
    .req .meta { color: var(--cinza-texto); font-size: .84rem; margin-top: .2rem; }
    .req .acoes { display: flex; gap: .5rem; flex-wrap: wrap; margin-top: .7rem; }
    .badge { font-size: .72rem; font-weight: 800; padding: .15rem .6rem; border-radius: 999px; white-space: nowrap; }
    .b-aberta { background: #fef3c7; color: #92400e; } .b-aprovada { background: #dbeafe; color: #1e40af; }
    .b-negada { background: #fee2e2; color: #991b1b; } .b-finalizada { background: #dcfce7; color: #166534; }
    .b-cancelada { background: #e7ebf1; color: #5b6b80; }
    .ff { margin-bottom: .7rem; } .ff label { display:block; font-size:.82rem; color:var(--cinza-texto); margin-bottom:.2rem; }
    .det dt { font-size:.75rem; color:var(--cinza-texto); text-transform:uppercase; letter-spacing:.04em; margin-top:.6rem; }
    .det dd { margin:.1rem 0 0; color:var(--texto); }
    .anexo { display:inline-flex; align-items:center; gap:.35rem; border:1px solid var(--cinza-borda); border-radius:8px;
      padding:.3rem .6rem; margin:.3rem .3rem 0 0; cursor:pointer; font-size:.82rem; background:none; color:var(--azul); }
  `],
  template: `
    <div class="flex-between" style="flex-wrap:wrap;gap:.6rem;margin-bottom:.3rem">
      <h2 style="margin:0">💰 Requisições da tesouraria</h2>
      @if (podeAbrir()) { <button class="btn" (click)="abrirNova()">+ Nova requisição</button> }
    </div>
    <p class="muted">{{ ehTesoureiro() ? 'Avalie as solicitações dos ministérios.' : 'Solicite recursos e preste contas com a nota fiscal.' }}</p>

    <div class="card">
      <div class="barra">
        <div class="form-group"><label>Status</label>
          <select [(ngModel)]="filtro" (ngModelChange)="carregar()">
            <option [ngValue]="null">Todos</option>
            <option value="ABERTA">Aguardando</option>
            <option value="APROVADA">Aprovadas</option>
            <option value="FINALIZADA">Finalizadas</option>
            <option value="NEGADA">Negadas</option>
            <option value="CANCELADA">Canceladas</option>
          </select>
        </div>
      </div>

      @if (carregando()) {
        <div class="spinner-wrap muted">Carregando...</div>
      } @else if (itens().length === 0) {
        <p class="muted text-center">Nenhuma requisição.</p>
      } @else {
        @for (r of itens(); track r.id) {
          <div class="req">
            <div class="topo">
              <span class="num">{{ r.numero }}</span>
              <span class="badge" [class]="classe(r.status)">{{ rotulo(r.status) }}</span>
              @if (r.possuiComprovante) { <span class="badge b-aprovada" title="Comprovante de transferência anexado — abra os Detalhes para ver">🧾 Comprovante</span> }
              <span class="val">{{ brl(r.valorSolicitado) }}</span>
            </div>
            <div class="meta">
              {{ r.ministerio }}@if (r.nomeEvento) { · {{ r.nomeEvento }} } · por {{ r.solicitanteNome }}
              · {{ r.criadoEm | date:'dd/MM/yyyy' }}
            </div>
            <div class="acoes">
              <button class="btn btn-outline btn-sm" (click)="abrirDetalhe(r)">Detalhes</button>
              @if (ehTesoureiro() && r.status === 'ABERTA') {
                <button class="btn btn-verde btn-sm" (click)="abrirAvaliar(r)">Avaliar</button>
              }
              @if (souDono(r) && r.status === 'APROVADA') {
                <button class="btn btn-dourado btn-sm" (click)="abrirFinalizar(r)">Anexar nota / finalizar</button>
              }
              @if (souDono(r) && r.status === 'ABERTA') {
                <button class="btn btn-outline btn-sm" (click)="cancelar(r)">Cancelar</button>
              }
            </div>
          </div>
        }
      }
    </div>

    <!-- Nova requisição -->
    @if (modalNova()) {
      <div class="modal-backdrop" (click)="modalNova.set(false)">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header"><h3>Nova requisição</h3></div>
          <div class="modal-body">
            <div class="ff"><label>Ministério *</label><input type="text" [(ngModel)]="form.ministerio" maxlength="120" placeholder="Ex.: Louvor" /></div>
            <div class="ff"><label>Nome do evento</label><input type="text" [(ngModel)]="form.nomeEvento" maxlength="160" placeholder="Ex.: Culto de Natal" /></div>
            <div class="ff"><label>Destinação *</label><input type="text" [(ngModel)]="form.destinacao" maxlength="300" placeholder="Para onde vai o recurso" /></div>
            <div class="ff"><label>Motivo *</label><textarea rows="3" [(ngModel)]="form.motivo" placeholder="Justificativa do pedido"></textarea></div>
            <div class="ff"><label>Valor solicitado (R$) *</label><input type="number" min="0.01" step="0.01" [(ngModel)]="form.valorSolicitado" /></div>
            <div class="ff"><label>Data necessária</label><input type="date" [(ngModel)]="form.dataNecessidade" /></div>
            <div class="ff"><label>Forma de repasse *</label>
              <select [(ngModel)]="form.formaRepasse">
                <option value="DINHEIRO">Dinheiro</option>
                <option value="PIX">PIX</option>
              </select>
            </div>
            @if (form.formaRepasse === 'PIX') {
              <div class="ff"><label>Tipo da chave PIX *</label>
                <select [(ngModel)]="form.pixTipo">
                  <option [ngValue]="null">— selecione —</option>
                  <option value="CPF">CPF</option>
                  <option value="EMAIL">E-mail</option>
                  <option value="TELEFONE">Telefone</option>
                </select>
                <small class="muted">A chave deve ser sua (do solicitante). Chave aleatória não é aceita.</small>
              </div>
              <div class="ff"><label>Chave PIX *</label><input type="text" [(ngModel)]="form.pixChave" maxlength="140" placeholder="Seu CPF, e-mail ou telefone" /></div>
            }
          </div>
          <div class="modal-footer">
            <button class="btn btn-outline" (click)="modalNova.set(false)">Cancelar</button>
            <button class="btn btn-verde" (click)="salvarNova()" [disabled]="salvando()">{{ salvando() ? 'Enviando...' : 'Enviar requisição' }}</button>
          </div>
        </div>
      </div>
    }

    <!-- Avaliar -->
    @if (avaliar(); as r) {
      <div class="modal-backdrop" (click)="avaliar.set(null)">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header"><h3>Avaliar {{ r.numero }}</h3></div>
          <div class="modal-body">
            <p class="muted" style="margin-top:0">{{ r.ministerio }} · solicitado {{ brl(r.valorSolicitado) }}<br>{{ r.motivo }}</p>
            <div class="ff"><label>Valor aprovado (R$)</label><input type="number" min="0.01" step="0.01" [(ngModel)]="valorAprovado" /></div>
            <div class="ff"><label>Parecer / observação</label><textarea rows="2" [(ngModel)]="parecer" placeholder="Opcional"></textarea></div>
            @if (r.formaRepasse === 'PIX') {
              <div class="ff muted" style="font-size:.85rem">PIX ({{ rotuloPix(r.pixTipo) }}): <b>{{ r.pixChave }}</b></div>
            }
            <div class="ff"><label>Comprovante de transferência (opcional) — PDF ou imagem</label>
              <input type="file" accept=".pdf,image/*" (change)="onComprovante($event)" />
              @if (comprovante) { <small class="muted">{{ comprovante.name }}</small> }
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-perigo" (click)="negar(r)" [disabled]="avaliando()">Negar</button>
            <button class="btn btn-verde" (click)="aprovar(r)" [disabled]="avaliando()">Aprovar</button>
          </div>
        </div>
      </div>
    }

    <!-- Finalizar -->
    @if (finalizar(); as r) {
      <div class="modal-backdrop" (click)="finalizar.set(null)">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header"><h3>Finalizar {{ r.numero }}</h3></div>
          <div class="modal-body">
            <p class="muted" style="margin-top:0">Anexe a nota fiscal e informe o valor gasto para prestar contas.</p>
            <div class="ff"><label>Valor gasto (R$)</label><input type="number" min="0" step="0.01" [(ngModel)]="valorGasto" /></div>
            <div class="ff"><label>Observação</label><textarea rows="2" [(ngModel)]="obsFinal"></textarea></div>
            <div class="ff"><label>Nota(s) fiscal(is) * — PDF ou imagem</label>
              <input type="file" multiple accept=".pdf,image/*" (change)="onFiles($event)" />
              @if (arquivos.length) { <small class="muted">{{ arquivos.length }} arquivo(s) selecionado(s)</small> }
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-outline" (click)="finalizar.set(null)">Cancelar</button>
            <button class="btn btn-verde" (click)="enviarFinalizacao(r)" [disabled]="finalizando()">{{ finalizando() ? 'Enviando...' : 'Finalizar' }}</button>
          </div>
        </div>
      </div>
    }

    <!-- Detalhe -->
    @if (detalhe(); as r) {
      <div class="modal-backdrop" (click)="detalhe.set(null)">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header"><h3>{{ r.numero }} <span class="badge" [class]="classe(r.status)">{{ rotulo(r.status) }}</span></h3></div>
          <div class="modal-body">
            <dl class="det" style="margin:0">
              <dt>Ministério</dt><dd>{{ r.ministerio }}</dd>
              @if (r.nomeEvento) { <dt>Evento</dt><dd>{{ r.nomeEvento }}</dd> }
              <dt>Destinação</dt><dd>{{ r.destinacao }}</dd>
              <dt>Motivo</dt><dd>{{ r.motivo }}</dd>
              <dt>Valor solicitado</dt><dd>{{ brl(r.valorSolicitado) }}</dd>
              @if (r.dataNecessidade) { <dt>Data necessária</dt><dd>{{ r.dataNecessidade | date:'dd/MM/yyyy' }}</dd> }
              <dt>Solicitante</dt><dd>{{ r.solicitanteNome }} · {{ r.criadoEm | date:'dd/MM/yyyy HH:mm' }}</dd>
              <dt>Forma de repasse</dt>
              <dd>{{ r.formaRepasse === 'PIX' ? 'PIX' : 'Dinheiro' }}@if (r.formaRepasse === 'PIX') { — {{ rotuloPix(r.pixTipo) }}: <b>{{ r.pixChave }}</b> }</dd>
              @if (r.avaliadoPorNome) {
                <dt>Avaliação</dt>
                <dd>{{ r.status === 'NEGADA' ? 'Negada' : 'Aprovada' }} por {{ r.avaliadoPorNome }}
                  @if (r.valorAprovado != null) { · {{ brl(r.valorAprovado) }} }
                  @if (r.parecerTesoureiro) { <br><em>{{ r.parecerTesoureiro }}</em> }
                </dd>
              }
              @if (r.status === 'FINALIZADA') {
                <dt>Prestação de contas</dt>
                <dd>Gasto {{ brl(r.valorGasto) }} · {{ r.finalizadoEm | date:'dd/MM/yyyy' }}
                  @if (r.observacaoFinal) { <br>{{ r.observacaoFinal }} }</dd>
              }
              @if (r.anexos.length) {
                <dt>Anexos</dt>
                <dd>@for (a of r.anexos; track a.id) { <button class="anexo" (click)="abrirAnexo(a.id)">{{ a.categoria === 'COMPROVANTE' ? '🧾' : '📎' }} {{ a.categoria === 'COMPROVANTE' ? 'Comprovante' : 'Nota fiscal' }}: {{ a.nome || 'anexo' }}</button> }</dd>
              }
            </dl>
          </div>
          <div class="modal-footer"><button class="btn btn-outline" (click)="detalhe.set(null)">Fechar</button></div>
        </div>
      </div>
    }
  `,
})
export class RequisicoesComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  auth = inject(AuthService);

  itens = signal<Requisicao[]>([]);
  carregando = signal(true);
  filtro: string | null = null;

  modalNova = signal(false);
  form: RequisicaoRequest = this.vazio();
  salvando = signal(false);

  avaliar = signal<Requisicao | null>(null);
  valorAprovado: number | null = null;
  parecer = '';
  comprovante: File | null = null;
  avaliando = signal(false);

  finalizar = signal<Requisicao | null>(null);
  valorGasto: number | null = null;
  obsFinal = '';
  arquivos: File[] = [];
  finalizando = signal(false);

  detalhe = signal<Requisicao | null>(null);

  constructor() { this.carregar(); }

  ehTesoureiro(): boolean { return this.auth.isTesoureiro() || this.auth.isAdmin(); }
  podeAbrir(): boolean { return this.auth.isLider() || this.auth.isAdmin(); }
  // O líder só recebe as próprias requisições na lista; ADMIN é superusuário. Backend valida o dono.
  souDono(_r: Requisicao): boolean { return this.auth.isLider() || this.auth.isAdmin(); }

  private vazio(): RequisicaoRequest {
    return { ministerio: '', nomeEvento: '', destinacao: '', motivo: '', valorSolicitado: 0, dataNecessidade: null, formaRepasse: 'DINHEIRO', pixTipo: null, pixChave: null };
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarRequisicoes(this.filtro).subscribe({
      next: (l) => { this.itens.set(l); this.carregando.set(false); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Falha ao carregar requisições.'); this.carregando.set(false); },
    });
  }

  abrirNova(): void { this.form = this.vazio(); this.modalNova.set(true); }
  salvarNova(): void {
    if (!this.form.ministerio?.trim() || !this.form.destinacao?.trim() || !this.form.motivo?.trim()) {
      this.toast.erro('Preencha ministério, destinação e motivo.'); return;
    }
    if (!this.form.valorSolicitado || this.form.valorSolicitado <= 0) { this.toast.erro('Informe um valor válido.'); return; }
    if (this.form.formaRepasse === 'PIX' && (!this.form.pixTipo || !this.form.pixChave?.trim())) {
      this.toast.erro('Para PIX, informe o tipo e a chave.'); return;
    }
    this.salvando.set(true);
    this.api.criarRequisicao({ ...this.form, dataNecessidade: this.form.dataNecessidade || null }).subscribe({
      next: () => { this.toast.sucesso('Requisição enviada! A tesouraria foi avisada.'); this.salvando.set(false); this.modalNova.set(false); this.carregar(); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao enviar.'); this.salvando.set(false); },
    });
  }

  abrirAvaliar(r: Requisicao): void { this.valorAprovado = r.valorSolicitado; this.parecer = ''; this.comprovante = null; this.avaliar.set(r); }
  onComprovante(e: Event): void { this.comprovante = (e.target as HTMLInputElement).files?.[0] ?? null; }
  rotuloPix(t?: string | null): string { return t === 'CPF' ? 'CPF' : t === 'EMAIL' ? 'e-mail' : t === 'TELEFONE' ? 'telefone' : '—'; }
  aprovar(r: Requisicao): void {
    this.avaliando.set(true);
    this.api.aprovarRequisicao(r.id, this.valorAprovado, this.parecer || null, this.comprovante).subscribe({
      next: () => { this.toast.sucesso('Requisição aprovada.'); this.avaliando.set(false); this.avaliar.set(null); this.carregar(); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao aprovar.'); this.avaliando.set(false); },
    });
  }
  negar(r: Requisicao): void {
    this.avaliando.set(true);
    this.api.negarRequisicao(r.id, this.parecer || null).subscribe({
      next: () => { this.toast.sucesso('Requisição negada.'); this.avaliando.set(false); this.avaliar.set(null); this.carregar(); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao negar.'); this.avaliando.set(false); },
    });
  }

  abrirFinalizar(r: Requisicao): void { this.valorGasto = r.valorAprovado ?? r.valorSolicitado; this.obsFinal = ''; this.arquivos = []; this.finalizar.set(r); }
  onFiles(e: Event): void { this.arquivos = Array.from((e.target as HTMLInputElement).files ?? []); }
  enviarFinalizacao(r: Requisicao): void {
    if (!this.arquivos.length) { this.toast.erro('Anexe ao menos a nota fiscal.'); return; }
    this.finalizando.set(true);
    this.api.finalizarRequisicao(r.id, this.valorGasto, this.obsFinal || null, this.arquivos).subscribe({
      next: () => { this.toast.sucesso('Prestação de contas concluída!'); this.finalizando.set(false); this.finalizar.set(null); this.carregar(); },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao finalizar.'); this.finalizando.set(false); },
    });
  }

  async cancelar(r: Requisicao): Promise<void> {
    if (!(await this.confirm.pedir({ titulo: 'Cancelar requisição', mensagem: `Cancelar a requisição ${r.numero}?`, confirmar: 'Cancelar requisição', perigo: true }))) { return; }
    this.api.cancelarRequisicao(r.id).subscribe({
      next: () => { this.toast.sucesso('Requisição cancelada.'); this.carregar(); },
      error: (e) => this.toast.erro(e?.error?.message || 'Erro ao cancelar.'),
    });
  }

  abrirDetalhe(r: Requisicao): void {
    // carrega a versão com anexos
    this.api.buscarRequisicao(r.id).subscribe({
      next: (d) => this.detalhe.set(d),
      error: (e) => this.toast.erro(e?.error?.message || 'Não foi possível carregar os detalhes.'),
    });
  }
  abrirAnexo(id: number): void {
    this.api.baixarAnexo(id).subscribe({
      next: (blob) => { const url = URL.createObjectURL(blob); window.open(url, '_blank'); setTimeout(() => URL.revokeObjectURL(url), 60000); },
      error: () => this.toast.erro('Não foi possível abrir o anexo.'),
    });
  }

  brl(v: number | null | undefined): string {
    if (v == null) { return '—'; }
    return 'R$ ' + v.toFixed(2).replace('.', ',');
  }
  rotulo(s: StatusRequisicao): string {
    return { ABERTA: 'Aguardando', APROVADA: 'Aprovada · aguardando nota', NEGADA: 'Negada', FINALIZADA: 'Finalizada', CANCELADA: 'Cancelada' }[s];
  }
  classe(s: StatusRequisicao): string {
    return { ABERTA: 'b-aberta', APROVADA: 'b-aprovada', NEGADA: 'b-negada', FINALIZADA: 'b-finalizada', CANCELADA: 'b-cancelada' }[s];
  }
}
