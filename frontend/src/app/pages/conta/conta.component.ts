import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';

/** Troca da própria senha do usuário autenticado. */
@Component({
  selector: 'app-conta',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div style="margin-bottom:1.25rem">
      <h2>Minha conta</h2>
      <p class="muted">Trocar a senha de <strong>{{ auth.username() }}</strong>.</p>
    </div>

    <div class="card" style="max-width:460px">
      <form (ngSubmit)="salvar()">
        <div class="form-group">
          <label>Senha atual</label>
          <input type="password" autocomplete="current-password"
                 [(ngModel)]="senhaAtual" name="senhaAtual" />
        </div>
        <div class="form-group">
          <label>Nova senha</label>
          <input type="password" autocomplete="new-password"
                 [(ngModel)]="novaSenha" name="novaSenha" />
          <small class="muted">Mínimo de {{ SENHA_MIN }} caracteres.</small>
        </div>
        <div class="form-group">
          <label>Confirmar nova senha</label>
          <input type="password" autocomplete="new-password"
                 [(ngModel)]="confirma" name="confirma" />
          @if (confirma && !confere()) {
            <small style="color:#c53030">As senhas não conferem.</small>
          }
        </div>
        <button class="btn btn-verde" type="submit" [disabled]="!valido() || salvando()">
          {{ salvando() ? 'Salvando...' : 'Trocar senha' }}
        </button>
      </form>
    </div>
  `,
})
export class ContaComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private router = inject(Router);
  auth = inject(AuthService);

  readonly SENHA_MIN = 8;

  senhaAtual = '';
  novaSenha = '';
  confirma = '';
  salvando = signal(false);

  confere(): boolean {
    return this.novaSenha === this.confirma;
  }

  valido(): boolean {
    return this.senhaAtual.length > 0 &&
      this.novaSenha.length >= this.SENHA_MIN &&
      this.confere();
  }

  salvar(): void {
    if (!this.valido()) {
      this.toast.erro(`A nova senha deve ter ao menos ${this.SENHA_MIN} caracteres e conferir com a confirmação.`);
      return;
    }
    this.salvando.set(true);
    this.api.trocarSenha(this.senhaAtual, this.novaSenha).subscribe({
      next: () => {
        this.salvando.set(false);
        this.toast.sucesso('Senha alterada com sucesso.');
        this.router.navigate(['/']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.toast.erro(e?.error?.message || 'Não foi possível trocar a senha.');
      },
    });
  }
}
