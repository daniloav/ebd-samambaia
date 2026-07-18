import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse, Role } from './models';

const CHAVE_TOKEN = 'ebd_token';
const CHAVE_USER = 'ebd_user';
const CHAVE_ROLE = 'ebd_role';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = environment.apiUrl;

  readonly username = signal<string | null>(localStorage.getItem(CHAVE_USER));
  readonly role = signal<Role | null>(localStorage.getItem(CHAVE_ROLE) as Role | null);
  readonly logado = computed(() => !!this.token());
  readonly isAdmin = computed(() => this.role() === 'ADMIN');

  constructor(private http: HttpClient) {}

  token(): string | null {
    return localStorage.getItem(CHAVE_TOKEN);
  }

  login(username: string, senha: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.api}/auth/login`, { username, senha }).pipe(
      tap((res) => {
        localStorage.setItem(CHAVE_TOKEN, res.token);
        localStorage.setItem(CHAVE_USER, res.username);
        localStorage.setItem(CHAVE_ROLE, res.role);
        this.username.set(res.username);
        this.role.set(res.role);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(CHAVE_TOKEN);
    localStorage.removeItem(CHAVE_USER);
    localStorage.removeItem(CHAVE_ROLE);
    this.username.set(null);
    this.role.set(null);
  }
}
