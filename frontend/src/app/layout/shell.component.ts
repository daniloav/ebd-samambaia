import { Perfil } from '../core/models';
import { Component, OnInit, OnDestroy, effect, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';
import { ClasseContextService } from '../core/classe-context.service';
import { APP_VERSION } from '../version';
import { ConfirmDialogComponent } from '../core/confirm-dialog.component';
import { TemaService } from '../core/tema.service';
import { TelemetriaService } from '../core/telemetria.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, ConfirmDialogComponent],
  styles: [`
    .layout { display: flex; min-height: 100vh; }
    .sidebar {
      width: 240px; background: var(--azul); color: #fff;
      display: flex; flex-direction: column; flex-shrink: 0;
      position: sticky; top: 0; height: 100vh;
    }
    .marca { padding: 1.4rem 1.25rem; border-bottom: 1px solid rgba(255,255,255,.12); }
    .marca h1 { color: #fff; font-size: 1.05rem; margin: 0; line-height: 1.3; }
    .marca span { color: var(--dourado-claro); font-size: .78rem; }
    nav { flex: 1; padding: .75rem 0; overflow-y: auto; }
    nav a {
      display: flex; align-items: center; gap: .7rem;
      padding: .7rem 1.25rem; color: #cbd5e0; text-decoration: none;
      font-size: .92rem; font-weight: 500; border-left: 3px solid transparent;
    }
    nav a:hover { background: rgba(255,255,255,.06); color: #fff; text-decoration: none; }
    nav a.ativo { background: rgba(201,162,75,.16); color: #fff; border-left-color: var(--dourado); }
    .ico { font-size: 1.1rem; width: 1.3rem; text-align: center; }
    .seletor-classe { padding: .8rem 1.25rem; border-bottom: 1px solid rgba(255,255,255,.12); }
    .seletor-classe label { display: block; color: #98b2d4; font-size: .68rem; text-transform: uppercase;
                            letter-spacing: .05em; margin-bottom: .3rem; }
    .seletor-classe select { width: 100%; padding: .4rem .5rem; border-radius: 6px; border: none;
                             background: rgba(255,255,255,.1); color: #fff; font-size: .88rem; }
    .seletor-classe select option { color: #1a202c; }
    .grupo { padding: .9rem 1.25rem .3rem; font-size: .7rem; text-transform: uppercase;
             letter-spacing: .05em; color: #98b2d4; }
    .rodape { padding: 1rem 1.25rem; border-top: 1px solid rgba(255,255,255,.12); }
    .user { font-size: .85rem; margin-bottom: .5rem; }
    .user strong { display: block; }
    .user .perfil { color: var(--dourado-claro); font-size: .74rem; }
    .btn-sair {
      width: 100%; background: rgba(255,255,255,.1); color: #fff;
      border: none; padding: .5rem; border-radius: 6px; cursor: pointer; font-weight: 600;
    }
    .btn-sair:hover { background: rgba(255,255,255,.2); }
    .link-conta { display: block; color: #cbd5e0; font-size: .82rem; text-decoration: none;
                  margin-bottom: .55rem; }
    .link-conta:hover { color: #fff; text-decoration: underline; }
    .link-tema { background: none; border: none; padding: 0; cursor: pointer; font: inherit; text-align: left; }
    .conteudo { flex: 1; padding: 1.75rem 2rem; max-width: 1100px; }
    .topo-mobile { display: none; }
    .topbar-mobile { display: none; }
    .backdrop-menu { display: none; }
    @media (max-width: 820px) {
      .layout { flex-direction: column; }
      /* barra fixa de topo (fora da gaveta) com o botão do menu */
      .topbar-mobile {
        display: flex; align-items: center; gap: .7rem;
        position: fixed; top: 0; left: 0; right: 0; height: 54px; z-index: 1200;
        background: var(--azul); color: #fff; padding: 0 .9rem; box-shadow: var(--sombra-md);
      }
      .topbar-mobile h1 { margin: 0; font-size: 1rem; color: #fff; line-height: 1.1; }
      .topbar-mobile span { color: var(--dourado-claro); font-size: .72rem; }
      .hamburguer {
        background: rgba(255,255,255,.14); color: #fff; border: none;
        font-size: 1.25rem; line-height: 1; padding: .35rem .65rem; border-radius: 8px; cursor: pointer;
      }
      /* a barra lateral vira gaveta deslizante POR CIMA do conteúdo */
      .sidebar {
        position: fixed; top: 0; left: 0; height: 100vh; width: 270px; max-width: 82%;
        z-index: 1300; transform: translateX(-100%); transition: transform .25s ease;
        box-shadow: var(--sombra-md);
      }
      .sidebar.aberto { transform: translateX(0); }
      .backdrop-menu {
        display: block; position: fixed; inset: 0; background: rgba(0,0,0,.45);
        z-index: 1250; animation: fadein .2s ease;
      }
      .conteudo { padding: calc(54px + 1.1rem) 1.1rem 1.5rem; max-width: 100%; }
      .topo-mobile { display: flex; }
    }
    @keyframes fadein { from { opacity: 0; } to { opacity: 1; } }
  `],
  template: `
    <div class="layout">
      <header class="topbar-mobile">
        <button class="hamburguer" (click)="menuAberto.set(true)" aria-label="Abrir menu">☰</button>
        <div>
          <h1>EBD ICES</h1>
          <span>ICE Samambaia</span>
        </div>
      </header>
      @if (menuAberto()) {
        <div class="backdrop-menu" (click)="menuAberto.set(false)"></div>
      }
      <aside class="sidebar" [class.aberto]="menuAberto()">
        <div class="marca flex-between">
          <div>
            <h1>EBD ICES</h1>
            <span>ICE Samambaia · v{{ versao }}</span>
          </div>
          <button class="btn-sair topo-mobile" style="width:auto;padding:.3rem .6rem"
                  (click)="menuAberto.set(false)" aria-label="Fechar menu">✕</button>
        </div>
        @if (auth.perfisDisponiveis().length > 1) {
          <div class="seletor-classe">
            <label>Atuando como</label>
            <select aria-label="Atuando como (perfil de acesso)" [ngModel]="auth.perfilAtivo()" (ngModelChange)="trocarPerfil($event)">
              <option value="GESTAO">{{ auth.isAdmin() ? 'Administração' : 'Professor' }}</option>
              <option value="ALUNO">Aluno</option>
            </select>
          </div>
        }
        @if (auth.perfilAtivo() === 'GESTAO' && (auth.isProfessor() || auth.isAdmin()) && classeCtx.classes().length) {
          <div class="seletor-classe">
            <label>Turma</label>
            <select aria-label="Turma" [ngModel]="classeCtx.selecionadaId()" (ngModelChange)="classeCtx.selecionar($event)">
              @for (c of classeCtx.classes(); track c.id) {
                <option [ngValue]="c.id">{{ c.nome }}</option>
              }
            </select>
          </div>
        }
        <nav (click)="fecharNoMobile()">
          @if (auth.perfilAtivo() === 'ALUNO' && auth.isAluno()) {
            <a routerLink="/minha-frequencia" routerLinkActive="ativo"><span class="ico">📊</span> Minha frequência</a>
            <a routerLink="/minhas-provas" routerLinkActive="ativo"><span class="ico">🧠</span> Minhas provas</a>
            <a routerLink="/meu-ranking" routerLinkActive="ativo"><span class="ico">🏆</span> Ranking da turma</a>
            <a routerLink="/meu-boletim" routerLinkActive="ativo"><span class="ico">📄</span> Meu boletim</a>
          }
          @if (auth.perfilAtivo() === 'GESTAO' && (auth.isProfessor() || auth.isAdmin())) {
            <a routerLink="/painel" routerLinkActive="ativo"><span class="ico">🏠</span> Painel</a>
            <div class="grupo">Chamada</div>
            <a routerLink="/chamada" routerLinkActive="ativo"><span class="ico">✅</span> Fazer chamada</a>
            <a routerLink="/aulas" routerLinkActive="ativo"><span class="ico">📅</span> Aulas</a>
            <a routerLink="/alunos" routerLinkActive="ativo"><span class="ico">👥</span> Alunos</a>
            <a routerLink="/relatorio" routerLinkActive="ativo"><span class="ico">📊</span> Relatório</a>
            <a routerLink="/relatorio-visitantes" routerLinkActive="ativo"><span class="ico">🧑‍🤝‍🧑</span> Visitantes</a>
            <div class="grupo">Desafios</div>
            <a routerLink="/desafios" routerLinkActive="ativo"><span class="ico">🏆</span> Rankings</a>
            <a routerLink="/provas" routerLinkActive="ativo"><span class="ico">📝</span> Provas</a>
            <a routerLink="/boletim" routerLinkActive="ativo"><span class="ico">📄</span> Boletins</a>
            @if (auth.isAdmin()) {
              <div class="grupo">Administração</div>
              <a routerLink="/classes" routerLinkActive="ativo"><span class="ico">📚</span> Classes</a>
              <a routerLink="/usuarios" routerLinkActive="ativo"><span class="ico">🔑</span> Usuários</a>
              <a routerLink="/campanhas" routerLinkActive="ativo"><span class="ico">📣</span> Campanhas</a>
            <a routerLink="/relatorio-geral" routerLinkActive="ativo"><span class="ico">📋</span> Relatório geral</a>
            <a routerLink="/auditoria" routerLinkActive="ativo"><span class="ico">📜</span> Auditoria</a>
            <a routerLink="/uso" routerLinkActive="ativo"><span class="ico">📈</span> Uso</a>
            }
          }
          @if (auth.isAdmin() || auth.isTesoureiro() || auth.isLider()) {
            <div class="grupo">Tesouraria</div>
            <a routerLink="/requisicoes" routerLinkActive="ativo"><span class="ico">💰</span> Requisições</a>
          }
        </nav>
        <div class="rodape">
          <div class="user">
            <strong>{{ auth.username() }}</strong>
            <span class="perfil">{{ perfilLabel() }}</span>
          </div>
          <button class="link-conta link-tema" (click)="tema.alternar()">
            {{ tema.escuroEfetivo() ? '☀️ Tema claro' : '🌙 Tema escuro' }}
          </button>
          <a class="link-conta" routerLink="/conta" (click)="fecharNoMobile()">🔒 Trocar senha</a>
          <button class="btn-sair" (click)="sair()">Sair</button>
        </div>
      </aside>
      <main class="conteudo">
        <router-outlet />
      </main>
    </div>
    <app-confirm-dialog />
  `,
})
export class ShellComponent implements OnInit, OnDestroy {
  auth = inject(AuthService);
  tema = inject(TemaService);
  classeCtx = inject(ClasseContextService);
  private router = inject(Router);
  private api = inject(ApiService);
  private telemetria = inject(TelemetriaService);
  private pingId?: ReturnType<typeof setInterval>;
  menuAberto = signal(false);
  versao = APP_VERSION;

  constructor() {
    // trava a rolagem do body enquanto a gaveta está aberta (mobile)
    effect(() => {
      document.body.style.overflow = this.menuAberto() ? 'hidden' : '';
    });
  }

  ngOnInit(): void {
    if (this.auth.isProfessor() || this.auth.isAdmin()) {
      this.classeCtx.carregar();
    }
    // Heartbeat de presença (base do "online agora" no painel de uso).
    this.enviarPing();
    this.pingId = setInterval(() => this.enviarPing(), 60_000);
    // Instrumentação de uso por funcionalidade (item D do painel /uso): 1 page view por navegação.
    this.telemetria.pagina(this.recursoDaRota(this.router.url));
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.telemetria.pagina(this.recursoDaRota(e.urlAfterRedirects)));
  }

  /** Deriva a chave do recurso (1º segmento da rota) para a telemetria; ignora ids e query. */
  private recursoDaRota(url: string): string {
    const seg = (url || '').split('?')[0].split('#')[0].split('/').filter(Boolean);
    return seg.length ? seg[0] : 'painel';
  }

  ngOnDestroy(): void {
    if (this.pingId) { clearInterval(this.pingId); }
  }

  private enviarPing(): void {
    this.api.ping().subscribe({ error: () => {} });
  }

  perfilLabel(): string {
    if (this.auth.perfilAtivo() === 'ALUNO') { return 'Aluno'; }
    return this.auth.isAdmin() ? 'Administrador' : 'Professor';
  }

  trocarPerfil(p: Perfil): void {
    this.auth.trocarPerfil(p);
    this.router.navigate([p === 'ALUNO' ? '/minha-frequencia' : '/painel']);
    this.fecharNoMobile();
  }

  fecharNoMobile(): void {
    this.menuAberto.set(false);
  }

  sair(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
