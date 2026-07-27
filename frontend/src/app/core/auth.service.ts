import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse, Perfil } from './models';

const CHAVE_TOKEN = 'ebd_token';
const CHAVE_USER = 'ebd_user';
const CHAVE_ADMIN = 'ebd_eh_admin';
const CHAVE_PROF = 'ebd_eh_professor';
const CHAVE_ALU = 'ebd_eh_aluno';
const CHAVE_PERFIL = 'ebd_perfil';
const CHAVE_TROCAR = 'ebd_trocar_senha';
const CHAVE_TES = 'ebd_eh_tesoureiro';
const CHAVE_LID = 'ebd_eh_lider';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = environment.apiUrl;

  // token reativo (signal) para que `logado` recalcule após login/logout.
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(CHAVE_TOKEN));

  readonly username = signal<string | null>(localStorage.getItem(CHAVE_USER));
  readonly precisaTrocarSenha = signal<boolean>(localStorage.getItem(CHAVE_TROCAR) === '1');
  // Papéis = capacidades (flags). Um usuário pode acumular vários.
  private readonly ehAdminSig = signal<boolean>(localStorage.getItem(CHAVE_ADMIN) === '1');
  private readonly ehProfessorSig = signal<boolean>(localStorage.getItem(CHAVE_PROF) === '1');
  private readonly ehAlunoSig = signal<boolean>(localStorage.getItem(CHAVE_ALU) === '1');
  private readonly ehTesoureiroSig = signal<boolean>(localStorage.getItem(CHAVE_TES) === '1');
  private readonly ehLiderSig = signal<boolean>(localStorage.getItem(CHAVE_LID) === '1');
  readonly logado = computed(() => this.tokenSignal() !== null);
  readonly isAdmin = computed(() => this.ehAdminSig());
  readonly isProfessor = computed(() => this.ehProfessorSig());
  readonly isAluno = computed(() => this.ehAlunoSig());
  readonly isTesoureiro = computed(() => this.isAdmin() || this.ehTesoureiroSig());
  readonly isLider = computed(() => this.isAdmin() || this.ehLiderSig());

  // Alternador de perfil: 'GESTAO' (professor/admin) x 'ALUNO'. Só troca o foco da UI.
  readonly perfisDisponiveis = computed<Perfil[]>(() => {
    const ps: Perfil[] = [];
    if (this.isProfessor() || this.isAdmin()) { ps.push('GESTAO'); }
    if (this.isAluno()) { ps.push('ALUNO'); }
    return ps;
  });
  readonly perfilAtivo = signal<Perfil>((localStorage.getItem(CHAVE_PERFIL) as Perfil) || 'GESTAO');

  trocarPerfil(p: Perfil): void {
    localStorage.setItem(CHAVE_PERFIL, p);
    this.perfilAtivo.set(p);
  }

  constructor(private http: HttpClient) {}

  token(): string | null {
    return this.tokenSignal();
  }

  login(username: string, senha: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.api}/auth/login`, { username, senha }).pipe(
      tap((res) => {
        localStorage.setItem(CHAVE_TOKEN, res.token);
        localStorage.setItem(CHAVE_USER, res.username);
        localStorage.setItem(CHAVE_TROCAR, res.precisaTrocarSenha ? '1' : '0');
        localStorage.setItem(CHAVE_ADMIN, res.ehAdmin ? '1' : '0');
        localStorage.setItem(CHAVE_PROF, res.ehProfessor ? '1' : '0');
        localStorage.setItem(CHAVE_ALU, res.ehAluno ? '1' : '0');
        localStorage.setItem(CHAVE_TES, res.ehTesoureiro ? '1' : '0');
        localStorage.setItem(CHAVE_LID, res.ehLider ? '1' : '0');
        this.tokenSignal.set(res.token);
        this.username.set(res.username);
        this.precisaTrocarSenha.set(!!res.precisaTrocarSenha);
        this.ehAdminSig.set(!!res.ehAdmin);
        this.ehProfessorSig.set(!!res.ehProfessor);
        this.ehAlunoSig.set(!!res.ehAluno);
        this.ehTesoureiroSig.set(!!res.ehTesoureiro);
        this.ehLiderSig.set(!!res.ehLider);
        // perfil inicial: gestão se tiver; senão aluno
        const perfil: Perfil = (res.ehProfessor || res.ehAdmin) ? 'GESTAO' : 'ALUNO';
        localStorage.setItem(CHAVE_PERFIL, perfil);
        this.perfilAtivo.set(perfil);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(CHAVE_TOKEN);
    localStorage.removeItem(CHAVE_USER);
    localStorage.removeItem(CHAVE_TROCAR);
    localStorage.removeItem(CHAVE_ADMIN);
    localStorage.removeItem(CHAVE_PROF);
    localStorage.removeItem(CHAVE_ALU);
    localStorage.removeItem(CHAVE_PERFIL);
    localStorage.removeItem(CHAVE_TES);
    localStorage.removeItem(CHAVE_LID);
    this.tokenSignal.set(null);
    this.username.set(null);
    this.precisaTrocarSenha.set(false);
    this.ehAdminSig.set(false);
    this.ehProfessorSig.set(false);
    this.ehAlunoSig.set(false);
    this.ehTesoureiroSig.set(false);
    this.ehLiderSig.set(false);
  }

  /** Marca que o 1º acesso foi concluído (senha trocada). */
  senhaTrocada(): void {
    localStorage.setItem(CHAVE_TROCAR, '0');
    this.precisaTrocarSenha.set(false);
  }
}
