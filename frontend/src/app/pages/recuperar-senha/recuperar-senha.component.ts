import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

/** Passo 1 do "esqueci a senha": informar o e-mail para receber o link de redefinição. */
@Component({
  selector: 'app-recuperar-senha',
  standalone: true,
  imports: [FormsModule, RouterLink],
  styles: [`
    .tela { min-height:100vh; display:flex; align-items:center; justify-content:center;
      background: linear-gradient(135deg, var(--azul) 0%, var(--azul-claro) 100%); padding:1rem; }
    .caixa { background:#fff; border-radius:14px; box-shadow:var(--sombra-md); width:100%; max-width:380px; padding:2.25rem 2rem; }
    .logo { text-align:center; margin-bottom:1.3rem; }
    .logo .icone { font-size:2.4rem; } .logo h1 { margin:.4rem 0 .1rem; font-size:1.2rem; }
    .logo p { margin:0; color:var(--cinza-texto); font-size:.85rem; }
    .btn { width:100%; justify-content:center; margin-top:.4rem; padding:.7rem; }
    .voltar { display:block; text-align:center; margin-top:1.1rem; font-size:.82rem; color:var(--azul); text-decoration:none; }
    .voltar:hover { text-decoration:underline; }
    .ok { background:#e7f0e9; color:#3a6b4e; padding:.8rem .9rem; border-radius:9px; font-size:.88rem; line-height:1.5; }
  `],
  template: `
    <div class="tela">
      <div class="caixa">
        <div class="logo">
          <div class="icone">🔑</div>
          <h1>Recuperar acesso</h1>
          <p>Esqueceu o usuário ou a senha? A gente te ajuda.</p>
        </div>

        @if (!enviado()) {
          <form (ngSubmit)="enviar()">
            <div class="form-group">
              <label for="email">Seu e-mail cadastrado</label>
              <input id="email" type="email" name="email" [(ngModel)]="email"
                     autocomplete="email" required placeholder="voce@exemplo.com" />
            </div>
            <button class="btn" type="submit" [disabled]="enviando()">
              {{ enviando() ? 'Enviando...' : 'Enviar link de redefinição' }}
            </button>
          </form>
        } @else {
          <div class="ok">✅ Se o e-mail estiver cadastrado, enviamos para ele o seu usuário e um
            link para criar uma nova senha. Confira a sua caixa de entrada (e o spam).</div>
        }

        <a class="voltar" routerLink="/login">← Voltar ao login</a>
      </div>
    </div>
  `,
})
export class RecuperarSenhaComponent {
  private api = inject(ApiService);

  email = '';
  enviando = signal(false);
  enviado = signal(false);

  enviar(): void {
    if (!this.email?.trim()) { return; }
    this.enviando.set(true);
    // Resposta é sempre genérica (o backend não revela se o e-mail existe).
    this.api.esqueciSenha(this.email.trim()).subscribe({
      next: () => { this.enviando.set(false); this.enviado.set(true); },
      error: () => { this.enviando.set(false); this.enviado.set(true); },
    });
  }
}
