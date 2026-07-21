import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ClasseContextService } from '../../core/classe-context.service';
import { Campanha } from '../../core/models';

interface ImagemSel { file: File; url: string; }

@Component({
  selector: 'app-campanhas',
  standalone: true,
  imports: [FormsModule, DatePipe],
  styles: [`
    .thumbs { display: flex; flex-wrap: wrap; gap: .6rem; margin-top: .6rem; }
    .thumb { position: relative; width: 92px; height: 92px; border-radius: 8px; overflow: hidden;
             border: 1px solid var(--cinza-borda); background: var(--superficie-2); }
    .thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .thumb button { position: absolute; top: 2px; right: 2px; width: 22px; height: 22px; border: none;
                    border-radius: 50%; background: rgba(0,0,0,.6); color: #fff; cursor: pointer; line-height: 1; }
    .previa { background: #eef1f4; border-radius: 10px; padding: 1rem; }
    .previa-email { max-width: 600px; margin: 0 auto; background: #fff; border-radius: 10px; overflow: hidden;
                    box-shadow: var(--sombra); }
    .previa-head { background: #1b3a5b; padding: 16px 20px; color: #fff; }
    .previa-head .marca { font-weight: bold; }
    .previa-head .turma { color: #c9a24b; font-size: .8rem; }
    .previa-body { padding: 20px; color: #2d3748; }
    .previa-body img { display: block; width: 100%; border-radius: 8px; margin-bottom: 12px; }
    .previa-body h1 { font-size: 20px; color: #1b3a5b; margin: 0 0 12px; }
    .previa-body .msg { white-space: pre-wrap; line-height: 1.55; }
    .dica { font-size: .8rem; }
  `],
  template: `
    <div style="margin-bottom:1.25rem">
      <h2>Campanhas</h2>
      <p class="muted">Envie um e-mail em massa (com arte) aos alunos que optaram por receber avisos.</p>
    </div>

    <div class="card" style="margin-bottom:1.5rem">
      <h3 style="margin-top:0">Nova campanha</h3>
      <div class="form-group">
        <label>Público-alvo</label>
        <select [(ngModel)]="classeId">
          <option [ngValue]="null">Todas as turmas</option>
          @for (c of classeCtx.classes(); track c.id) { <option [ngValue]="c.id">{{ c.nome }}</option> }
        </select>
      </div>
      <div class="form-group">
        <label>Título / assunto *</label>
        <input type="text" [(ngModel)]="titulo" maxlength="150" placeholder="Ex.: Culto especial neste domingo" />
      </div>
      <div class="form-group">
        <label>Mensagem *</label>
        <textarea [(ngModel)]="mensagem" rows="6" maxlength="5000"
                  placeholder="Escreva a mensagem que será enviada aos alunos..."></textarea>
      </div>

      <div class="form-group">
        <label>Imagens / arte (opcional)</label>
        <input type="file" accept="image/png,image/jpeg,image/gif,image/webp" multiple (change)="onArquivos($event)" />
        <small class="muted dica">Até {{ MAX }} imagens, {{ MAX_MB }} MB cada (JPG, PNG, GIF, WEBP). Aparecem no topo do e-mail.</small>
        @if (imagens().length) {
          <div class="thumbs">
            @for (im of imagens(); track im.url; let i = $index) {
              <div class="thumb">
                <img [src]="im.url" [alt]="im.file.name" />
                <button type="button" (click)="remover(i)" title="Remover">×</button>
              </div>
            }
          </div>
        }
      </div>

      <div class="flex-between" style="align-items:center;gap:1rem;flex-wrap:wrap">
        <span class="muted dica">Só recebem alunos <b>ativos</b>, com <b>e-mail</b> e <b>opt-in</b>.</span>
        <button class="btn btn-verde" (click)="enviar()" [disabled]="enviando()">
          {{ enviando() ? 'Enviando...' : 'Enviar campanha' }}
        </button>
      </div>
    </div>

    @if (titulo || mensagem || imagens().length) {
      <div class="card" style="margin-bottom:1.5rem">
        <h3 style="margin-top:0">Prévia do e-mail</h3>
        <div class="previa">
          <div class="previa-email">
            <div class="previa-head">
              <div class="marca">Escola Bíblica Dominical</div>
              <div class="turma">ICE Samambaia · {{ nomeAlvo() }}</div>
            </div>
            <div class="previa-body">
              @for (im of imagens(); track im.url) { <img [src]="im.url" [alt]="im.file.name" /> }
              <h1>{{ titulo || '(título)' }}</h1>
              <p style="margin:0 0 12px">Olá, [nome do aluno]!</p>
              <div class="msg">{{ mensagem || '(mensagem)' }}</div>
              <p style="margin:14px 0 0;color:#556">Escola Bíblica Dominical — ICE Samambaia 🙏</p>
            </div>
          </div>
        </div>
      </div>
    }

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
              <tr><th style="width:150px">Data</th><th>Título</th><th>Público</th>
                  <th style="width:90px">Arte</th><th style="width:90px">Enviados</th><th>Por</th></tr>
            </thead>
            <tbody>
              @for (c of campanhas(); track c.id) {
                <tr>
                  <td>{{ c.dataEnvio | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td>{{ c.titulo }}</td>
                  <td>{{ c.classeNome || 'Todas as turmas' }}</td>
                  <td>@if (c.imagens?.length) { <span class="badge badge-dourado">🖼️ {{ c.imagens?.length }}</span> } @else { — }</td>
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

  readonly MAX = 5;
  readonly MAX_MB = 2;
  readonly MAX_BYTES = this.MAX_MB * 1024 * 1024;
  readonly TIPOS = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];

  campanhas = signal<Campanha[]>([]);
  carregando = signal(true);
  enviando = signal(false);
  classeId: number | null = this.classeCtx.selecionadaId();
  titulo = '';
  mensagem = '';
  imagens = signal<ImagemSel[]>([]);

  constructor() {
    this.classeCtx.carregar();
    this.carregar();
  }

  nomeAlvo(): string {
    return this.classeId
      ? (this.classeCtx.classes().find((c) => c.id === this.classeId)?.nome ?? 'Turma')
      : 'Todas as turmas';
  }

  onArquivos(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    for (const f of arquivos) {
      if (this.imagens().length >= this.MAX) { this.toast.erro(`Máximo de ${this.MAX} imagens.`); break; }
      if (!this.TIPOS.includes(f.type)) { this.toast.erro(`${f.name}: use JPG, PNG, GIF ou WEBP.`); continue; }
      if (f.size > this.MAX_BYTES) { this.toast.erro(`${f.name}: excede ${this.MAX_MB} MB.`); continue; }
      this.imagens.update((l) => [...l, { file: f, url: URL.createObjectURL(f) }]);
    }
    input.value = '';
  }

  remover(i: number): void {
    this.imagens.update((l) => {
      const alvo = l[i];
      if (alvo) { URL.revokeObjectURL(alvo.url); }
      return l.filter((_, idx) => idx !== i);
    });
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarCampanhas().subscribe({
      next: (l) => { this.campanhas.set(l); this.carregando.set(false); },
      error: () => { this.toast.erro('Falha ao carregar campanhas.'); this.carregando.set(false); },
    });
  }

  enviar(): void {
    if (!this.titulo.trim()) { this.toast.erro('Informe o título.'); return; }
    if (!this.mensagem.trim()) { this.toast.erro('Informe a mensagem.'); return; }
    if (!confirm(`Enviar esta campanha para os alunos com opt-in de ${this.nomeAlvo()}?`)) { return; }

    this.enviando.set(true);
    const fd = new FormData();
    fd.append('titulo', this.titulo.trim());
    fd.append('mensagem', this.mensagem);
    if (this.classeId != null) { fd.append('classeId', String(this.classeId)); }
    for (const im of this.imagens()) { fd.append('imagens', im.file, im.file.name); }

    this.api.criarCampanha(fd).subscribe({
      next: (c) => {
        this.toast.sucesso(`Campanha enviada para ${c.totalEnviados} aluno(s).`);
        this.titulo = '';
        this.mensagem = '';
        this.imagens().forEach((im) => URL.revokeObjectURL(im.url));
        this.imagens.set([]);
        this.enviando.set(false);
        this.carregar();
      },
      error: (e) => { this.toast.erro(e?.error?.message || 'Erro ao enviar campanha.'); this.enviando.set(false); },
    });
  }
}
