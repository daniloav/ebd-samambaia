import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';

/** Passo 2: o usuário chega pelo link do e-mail (?token=...) e define a nova senha. */
@Component({
  selector: 'app-redefinir-senha',
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
    .erro { background:#fed7d7; color:#822727; padding:.6rem .8rem; border-radius:8px; font-size:.85rem; margin-bottom:1rem; }
    .muted { color:var(--cinza-texto); font-size:.82rem; text-align:center; margin:-.4rem 0 1rem; }
  `],
  template: `
    <div class="tela">
      <div class="caixa">
        <div class="logo">
          <div class="icone">🔒</div>
          <h1>Criar nova senha</h1>
        </div>

        @if (carregando()) {
          <p class="muted">Verificando o link...</p>
        } @else if (!valido()) {
          <div class="erro">{{ erro() || 'Link inválido ou expirado.' }}</div>
          <a class="voltar" routerLink="/recuperar">Pedir um novo link</a>
        } @else {
          <p class="muted">Definindo a senha de <b>{{ username() }}</b></p>
          @if (erro()) { <div class="erro">{{ erro() }}</div> }
          <form (ngSubmit)="salvar()">
            <div class="form-group">
              <label for="s1">Nova senha</label>
              <input id="s1" type="password" name="s1" [(ngModel)]="senha"
                     autocomplete="new-password" required minlength="8" />
            </div>
            <div class="form-group">
              <label for="s2">Repita a nova senha</label>
              <input id="s2" type="password" name="s2" [(ngModel)]="senha2"
                     autocomplete="new-password" required minlength="8" />
            </div>
            <button class="btn" type="submit" [disabled]="salvando()">
              {{ salvando() ? 'Salvando...' : 'Salvar nova senha' }}
            </button>
          </form>
          <a class="voltar" routerLink="/login">← Voltar ao login</a>
        }
      </div>
    </div>
  `,
})
export class RedefinirSenhaComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  private token = '';
  carregando = signal(true);
  valido = signal(false);
  username = signal('');
  erro = signal<string | null>(null);
  senha = '';
  senha2 = '';
  salvando = signal(false);

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) { this.carregando.set(false); this.erro.set('Link inválido.'); return; }
    this.api.validarTokenReset(this.token).subscribe({
      next: (r) => { this.username.set(r.username); this.valido.set(true); this.carregando.set(false); },
      error: (e) => { this.erro.set(e?.error?.message || 'Link inválido ou expirado.'); this.carregando.set(false); },
    });
  }

  salvar(): void {
    if (!this.senha || this.senha.length < 8) { this.erro.set('A senha deve ter pelo menos 8 caracteres.'); return; }
    if (this.senha !== this.senha2) { this.erro.set('As senhas não conferem.'); return; }
    this.erro.set(null);
    this.salvando.set(true);
    this.api.redefinirSenha(this.token, this.senha).subscribe({
      next: () => {
        this.salvando.set(false);
        this.toast.sucesso('Senha redefinida! Entre com a nova senha.');
        this.router.navigate(['/login']);
      },
      error: (e) => { this.salvando.set(false); this.erro.set(e?.error?.message || 'Não foi possível redefinir.'); },
    });
  }
}
