import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Aluno, AlunoRequest, Aula, AulaRequest, ChamadaResponse, Classe, ClasseRequest,
  DesafiosResponse, NotasProvaResponse, Prova, ProvaRequest, RelatorioPresencaResponse,
  Usuario, UsuarioRequest,
} from './models';

/** Cliente único para todos os endpoints da API. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // ---------- Alunos ----------
  listarAlunos(apenasAtivos = false, classeId?: number | null): Observable<Aluno[]> {
    let params = new HttpParams().set('apenasAtivos', apenasAtivos);
    if (classeId) params = params.set('classeId', classeId);
    return this.http.get<Aluno[]>(`${this.api}/alunos`, { params });
  }
  criarAluno(a: AlunoRequest): Observable<Aluno> {
    return this.http.post<Aluno>(`${this.api}/alunos`, a);
  }
  atualizarAluno(id: number, a: AlunoRequest): Observable<Aluno> {
    return this.http.put<Aluno>(`${this.api}/alunos/${id}`, a);
  }
  deletarAluno(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/alunos/${id}`);
  }

  // ---------- Aulas ----------
  listarAulas(classeId?: number | null): Observable<Aula[]> {
    let params = new HttpParams();
    if (classeId) params = params.set('classeId', classeId);
    return this.http.get<Aula[]>(`${this.api}/aulas`, { params });
  }
  criarAula(a: AulaRequest): Observable<Aula> {
    return this.http.post<Aula>(`${this.api}/aulas`, a);
  }
  atualizarAula(id: number, a: AulaRequest): Observable<Aula> {
    return this.http.put<Aula>(`${this.api}/aulas/${id}`, a);
  }
  deletarAula(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/aulas/${id}`);
  }

  // ---------- Chamada ----------
  obterChamada(aulaId: number): Observable<ChamadaResponse> {
    return this.http.get<ChamadaResponse>(`${this.api}/aulas/${aulaId}/chamada`);
  }
  salvarChamada(aulaId: number, itens: any[]): Observable<ChamadaResponse> {
    return this.http.put<ChamadaResponse>(`${this.api}/aulas/${aulaId}/chamada`, { itens });
  }

  // ---------- Relatório ----------
  relatorioPresencas(inicio?: string, fim?: string, classeId?: number | null): Observable<RelatorioPresencaResponse> {
    let params = new HttpParams();
    if (inicio) params = params.set('inicio', inicio);
    if (fim) params = params.set('fim', fim);
    if (classeId) params = params.set('classeId', classeId);
    return this.http.get<RelatorioPresencaResponse>(`${this.api}/relatorios/presencas`, { params });
  }

  // ---------- Provas ----------
  listarProvas(classeId?: number | null): Observable<Prova[]> {
    let params = new HttpParams();
    if (classeId) params = params.set('classeId', classeId);
    return this.http.get<Prova[]>(`${this.api}/provas`, { params });
  }
  criarProva(p: ProvaRequest): Observable<Prova> {
    return this.http.post<Prova>(`${this.api}/provas`, p);
  }
  atualizarProva(id: number, p: ProvaRequest): Observable<Prova> {
    return this.http.put<Prova>(`${this.api}/provas/${id}`, p);
  }
  deletarProva(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/provas/${id}`);
  }
  obterNotas(provaId: number): Observable<NotasProvaResponse> {
    return this.http.get<NotasProvaResponse>(`${this.api}/provas/${provaId}/notas`);
  }
  salvarNotas(provaId: number, itens: any[]): Observable<NotasProvaResponse> {
    return this.http.put<NotasProvaResponse>(`${this.api}/provas/${provaId}/notas`, { itens });
  }

  // ---------- Desafios ----------
  rankings(classeId?: number | null): Observable<DesafiosResponse> {
    let params = new HttpParams();
    if (classeId) params = params.set('classeId', classeId);
    return this.http.get<DesafiosResponse>(`${this.api}/desafios/rankings`, { params });
  }

  // ---------- Classes ----------
  listarClasses(apenasAtivas = false): Observable<Classe[]> {
    const params = new HttpParams().set('apenasAtivas', apenasAtivas);
    return this.http.get<Classe[]>(`${this.api}/classes`, { params });
  }
  criarClasse(c: ClasseRequest): Observable<Classe> {
    return this.http.post<Classe>(`${this.api}/classes`, c);
  }
  atualizarClasse(id: number, c: ClasseRequest): Observable<Classe> {
    return this.http.put<Classe>(`${this.api}/classes/${id}`, c);
  }
  deletarClasse(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/classes/${id}`);
  }

  // ---------- Usuários ----------
  listarUsuarios(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(`${this.api}/usuarios`);
  }
  criarUsuario(u: UsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.api}/usuarios`, u);
  }
  atualizarUsuario(id: number, u: UsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.api}/usuarios/${id}`, u);
  }
  deletarUsuario(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/usuarios/${id}`);
  }
}
