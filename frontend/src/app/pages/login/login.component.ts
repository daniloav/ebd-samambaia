import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { APP_VERSION } from '../../version';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  styles: [`
    .tela {
      min-height: 100vh; display: flex; align-items: center; justify-content: center;
      background: linear-gradient(135deg, var(--azul) 0%, var(--azul-claro) 100%);
      padding: 1rem;
    }
    .caixa {
      background: #fff; border-radius: 14px; box-shadow: var(--sombra-md);
      width: 100%; max-width: 380px; padding: 2.25rem 2rem;
    }
    .logo { text-align: center; margin-bottom: 1.5rem; }
    .logo .icone { font-size: 2.6rem; }
    .logo h1 { margin: .4rem 0 .1rem; font-size: 1.3rem; }
    .logo p { margin: 0; color: var(--cinza-texto); font-size: .85rem; }
    .btn { width: 100%; justify-content: center; margin-top: .5rem; padding: .7rem; }
    .erro { background: #fed7d7; color: #822727; padding: .6rem .8rem;
            border-radius: 8px; font-size: .85rem; margin-bottom: 1rem; }
    .dica { margin-top: 1.25rem; font-size: .78rem; color: var(--cinza-texto);
            text-align: center; line-height: 1.5; }
    .versao { display:block; margin-top:.5rem; opacity:.55; font-size:.72rem; }
    .tutorial { display:flex; align-items:center; justify-content:center; gap:.5rem;
                margin-top:1.1rem; padding:.62rem .8rem; border:1px solid #d7dee8;
                border-radius:9px; color:#2c4770; font-size:.86rem; font-weight:600;
                text-decoration:none; transition:background .15s, border-color .15s; }
    .tutorial:hover { background:#f4f7fb; border-color:#2c4770; text-decoration:none; }
  `],
  template: `
    <div class="tela">
      <div class="caixa">
        <div class="logo">
          <div class="icone">📖</div>
          <h1>EBD ICES</h1>
          <p>Igreja Cristã Evangélica · Samambaia</p>
        </div>

        @if (erro()) { <div class="erro">{{ erro() }}</div> }

        <form (ngSubmit)="entrar()">
          <div class="form-group">
            <label for="user">Usuário</label>
            <input id="user" type="text" name="username" [(ngModel)]="username"
                   autocomplete="username" required />
          </div>
          <div class="form-group">
            <label for="senha">Senha</label>
            <input id="senha" type="password" name="senha" [(ngModel)]="senha"
                   autocomplete="current-password" required />
          </div>
          <button class="btn" type="submit" [disabled]="carregando()">
            {{ carregando() ? 'Entrando...' : 'Entrar' }}
          </button>
        </form>

        <a class="tutorial" href="/assets/tutoriais/index.html" target="_blank" rel="noopener">
          📖 Primeira vez? Veja como usar o app
        </a>

        <p class="dica">Acesso restrito à coordenação da classe.
          <span class="versao">v{{ versao }}</span></p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  versao = APP_VERSION;
  private auth = inject(AuthService);
  private router = inject(Router);

  username = '';
  senha = '';
  carregando = signal(false);
  erro = signal<string | null>(null);

  entrar(): void {
    if (!this.username || !this.senha) {
      this.erro.set('Informe usuário e senha.');
      return;
    }
    this.carregando.set(true);
    this.erro.set(null);
    this.auth.login(this.username, this.senha).subscribe({
      next: () => this.router.navigate([this.auth.precisaTrocarSenha() ? '/conta' : '/painel']),
      error: () => {
        this.erro.set('Usuário ou senha inválidos.');
        this.carregando.set(false);
      },
    });
  }
}
