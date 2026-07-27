import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse, Role } from './models';

const CHAVE_TOKEN = 'ebd_token';
const CHAVE_USER = 'ebd_user';
const CHAVE_ROLE = 'ebd_role';
const CHAVE_TROCAR = 'ebd_trocar_senha';
const CHAVE_TES = 'ebd_eh_tesoureiro';
const CHAVE_LID = 'ebd_eh_lider';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = environment.apiUrl;

  // token reativo (signal) para que `logado` recalcule após login/logout.
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(CHAVE_TOKEN));

  readonly username = signal<string | null>(localStorage.getItem(CHAVE_USER));
  readonly role = signal<Role | null>(localStorage.getItem(CHAVE_ROLE) as Role | null);
  readonly precisaTrocarSenha = signal<boolean>(localStorage.getItem(CHAVE_TROCAR) === '1');
  // Capacidades funcionais (somam-se à role base); ADMIN tem tudo por padrão.
  private readonly ehTesoureiroSig = signal<boolean>(localStorage.getItem(CHAVE_TES) === '1');
  private readonly ehLiderSig = signal<boolean>(localStorage.getItem(CHAVE_LID) === '1');
  readonly logado = computed(() => this.tokenSignal() !== null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isProfessor = computed(() => this.role() === 'PROFESSOR');
  readonly isAluno = computed(() => this.role() === 'ALUNO');
  readonly isTesoureiro = computed(() => this.isAdmin() || this.ehTesoureiroSig());
  readonly isLider = computed(() => this.isAdmin() || this.ehLiderSig());

  constructor(private http: HttpClient) {}

  token(): string | null {
    return this.tokenSignal();
  }

  login(username: string, senha: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.api}/auth/login`, { username, senha }).pipe(
      tap((res) => {
        localStorage.setItem(CHAVE_TOKEN, res.token);
        localStorage.setItem(CHAVE_USER, res.username);
        localStorage.setItem(CHAVE_ROLE, res.role);
        localStorage.setItem(CHAVE_TROCAR, res.precisaTrocarSenha ? '1' : '0');
        localStorage.setItem(CHAVE_TES, res.ehTesoureiro ? '1' : '0');
        localStorage.setItem(CHAVE_LID, res.ehLider ? '1' : '0');
        this.tokenSignal.set(res.token);
        this.username.set(res.username);
        this.role.set(res.role);
        this.precisaTrocarSenha.set(!!res.precisaTrocarSenha);
        this.ehTesoureiroSig.set(!!res.ehTesoureiro);
        this.ehLiderSig.set(!!res.ehLider);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(CHAVE_TOKEN);
    localStorage.removeItem(CHAVE_USER);
    localStorage.removeItem(CHAVE_ROLE);
    localStorage.removeItem(CHAVE_TROCAR);
    localStorage.removeItem(CHAVE_TES);
    localStorage.removeItem(CHAVE_LID);
    this.tokenSignal.set(null);
    this.username.set(null);
    this.role.set(null);
    this.precisaTrocarSenha.set(false);
    this.ehTesoureiroSig.set(false);
    this.ehLiderSig.set(false);
  }

  /** Marca que o 1º acesso foi concluído (senha trocada). */
  senhaTrocada(): void {
    localStorage.setItem(CHAVE_TROCAR, '0');
    this.precisaTrocarSenha.set(false);
  }
}
