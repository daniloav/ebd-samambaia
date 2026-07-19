import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClasseContextService } from '../../core/classe-context.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  styles: [`
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin-bottom: 1.75rem; }
    .stat { background: #fff; border-radius: var(--raio); box-shadow: var(--sombra); padding: 1.25rem; }
    .stat .n { font-size: 2rem; font-weight: 800; color: var(--azul); }
    .stat .rot { color: var(--cinza-texto); font-size: .85rem; }
    .atalhos { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; }
    .atalho { background: #fff; border-radius: var(--raio); box-shadow: var(--sombra);
              padding: 1.4rem; text-decoration: none; color: inherit; transition: transform .1s, box-shadow .1s;
              border-top: 3px solid var(--dourado); }
    .atalho:hover { transform: translateY(-3px); box-shadow: var(--sombra-md); text-decoration: none; }
    .atalho .ico { font-size: 1.8rem; }
    .atalho h3 { margin: .5rem 0 .25rem; }
    .atalho p { margin: 0; color: var(--cinza-texto); font-size: .85rem; }
    .saudacao { margin-bottom: 1.5rem; }
  `],
  template: `
    <div class="saudacao">
      <h2>Olá, {{ auth.username() }}! 👋</h2>
      <p class="muted">Bem-vindo(a) ao painel da Escola Bíblica Dominical — Classe de Adultos.</p>
    </div>

    <div class="stats">
      <div class="stat"><div class="n">{{ totalAlunos() }}</div><div class="rot">Alunos ativos</div></div>
      <div class="stat"><div class="n">{{ totalAulas() }}</div><div class="rot">Aulas registradas</div></div>
      <div class="stat"><div class="n">{{ totalProvas() }}</div><div class="rot">Provas cadastradas</div></div>
    </div>

    <div class="atalhos">
      <a class="atalho" routerLink="/chamada">
        <div class="ico">✅</div><h3>Fazer chamada</h3>
        <p>Registre presença, Bíblia, revista e lição da aula.</p>
      </a>
      <a class="atalho" routerLink="/desafios">
        <div class="ico">🏆</div><h3>Ver rankings</h3>
        <p>Acompanhe os destaques da classe nos desafios.</p>
      </a>
      <a class="atalho" routerLink="/relatorio">
        <div class="ico">📊</div><h3>Relatório de presenças</h3>
        <p>Resumo de frequência por aluno e período.</p>
      </a>
      <a class="atalho" routerLink="/provas">
        <div class="ico">📝</div><h3>Provas e notas</h3>
        <p>Cadastre provas e lance as notas dos alunos.</p>
      </a>
    </div>
  `,
})
export class DashboardComponent {
  private api = inject(ApiService);
  auth = inject(AuthService);
  private classeCtx = inject(ClasseContextService);

  totalAlunos = signal(0);
  totalAulas = signal(0);
  totalProvas = signal(0);

  constructor() {
    effect(() => { this.classeCtx.selecionadaId(); this.carregar(); }, { allowSignalWrites: true });
  }

  private carregar(): void {
    const cid = this.classeCtx.selecionadaId();
    forkJoin({
      alunos: this.api.listarAlunos(true, cid),
      aulas: this.api.listarAulas(cid),
      provas: this.api.listarProvas(cid),
    }).subscribe({
      next: (r) => {
        this.totalAlunos.set(r.alunos.length);
        this.totalAulas.set(r.aulas.length);
        this.totalProvas.set(r.provas.length);
      },
      error: () => {},
    });
  }
}
