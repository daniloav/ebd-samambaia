import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { ClasseContextService } from '../core/classe-context.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
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
    .seletor-classe label { display: block; color: #7f9cbf; font-size: .68rem; text-transform: uppercase;
                            letter-spacing: .05em; margin-bottom: .3rem; }
    .seletor-classe select { width: 100%; padding: .4rem .5rem; border-radius: 6px; border: none;
                             background: rgba(255,255,255,.1); color: #fff; font-size: .88rem; }
    .seletor-classe select option { color: #1a202c; }
    .grupo { padding: .9rem 1.25rem .3rem; font-size: .7rem; text-transform: uppercase;
             letter-spacing: .05em; color: #7f9cbf; }
    .rodape { padding: 1rem 1.25rem; border-top: 1px solid rgba(255,255,255,.12); }
    .user { font-size: .85rem; margin-bottom: .5rem; }
    .user strong { display: block; }
    .user .perfil { color: var(--dourado-claro); font-size: .74rem; }
    .btn-sair {
      width: 100%; background: rgba(255,255,255,.1); color: #fff;
      border: none; padding: .5rem; border-radius: 6px; cursor: pointer; font-weight: 600;
    }
    .btn-sair:hover { background: rgba(255,255,255,.2); }
    .conteudo { flex: 1; padding: 1.75rem 2rem; max-width: 1100px; }
    .topo-mobile { display: none; }
    @media (max-width: 820px) {
      .layout { flex-direction: column; }
      .sidebar { width: 100%; height: auto; position: relative; }
      .sidebar.fechado nav, .sidebar.fechado .rodape { display: none; }
      .conteudo { padding: 1.25rem; }
      .topo-mobile { display: flex; }
    }
  `],
  template: `
    <div class="layout">
      <aside class="sidebar" [class.fechado]="!menuAberto()">
        <div class="marca flex-between">
          <div>
            <h1>EBD Adultos</h1>
            <span>ICE Samambaia</span>
          </div>
          <button class="btn-sair topo-mobile" style="width:auto;padding:.3rem .6rem"
                  (click)="menuAberto.set(!menuAberto())">☰</button>
        </div>
        @if (classeCtx.classes().length) {
          <div class="seletor-classe">
            <label>Turma</label>
            <select [ngModel]="classeCtx.selecionadaId()" (ngModelChange)="classeCtx.selecionar($event)">
              @for (c of classeCtx.classes(); track c.id) {
                <option [ngValue]="c.id">{{ c.nome }}</option>
              }
            </select>
          </div>
        }
        <nav (click)="fecharNoMobile()">
          <a routerLink="/painel" routerLinkActive="ativo"><span class="ico">🏠</span> Painel</a>
          <div class="grupo">Chamada</div>
          <a routerLink="/chamada" routerLinkActive="ativo"><span class="ico">✅</span> Fazer chamada</a>
          <a routerLink="/aulas" routerLinkActive="ativo"><span class="ico">📅</span> Aulas</a>
          <a routerLink="/alunos" routerLinkActive="ativo"><span class="ico">👥</span> Alunos</a>
          <a routerLink="/relatorio" routerLinkActive="ativo"><span class="ico">📊</span> Relatório</a>
          <div class="grupo">Desafios</div>
          <a routerLink="/desafios" routerLinkActive="ativo"><span class="ico">🏆</span> Rankings</a>
          <a routerLink="/provas" routerLinkActive="ativo"><span class="ico">📝</span> Provas</a>
          @if (auth.isAdmin()) {
            <div class="grupo">Administração</div>
            <a routerLink="/classes" routerLinkActive="ativo"><span class="ico">📚</span> Classes</a>
            <a routerLink="/usuarios" routerLinkActive="ativo"><span class="ico">🔑</span> Usuários</a>
            <a routerLink="/campanhas" routerLinkActive="ativo"><span class="ico">📣</span> Campanhas</a>
          }
        </nav>
        <div class="rodape">
          <div class="user">
            <strong>{{ auth.username() }}</strong>
            <span class="perfil">{{ auth.role() === 'ADMIN' ? 'Administrador' : 'Professor' }}</span>
          </div>
          <button class="btn-sair" (click)="sair()">Sair</button>
        </div>
      </aside>
      <main class="conteudo">
        <router-outlet />
      </main>
    </div>
  `,
})
export class ShellComponent implements OnInit {
  auth = inject(AuthService);
  classeCtx = inject(ClasseContextService);
  private router = inject(Router);
  menuAberto = signal(true);

  ngOnInit(): void {
    this.classeCtx.carregar();
  }

  fecharNoMobile(): void {
    if (window.innerWidth <= 820) {
      this.menuAberto.set(false);
    }
  }

  sair(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
