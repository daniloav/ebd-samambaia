import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Aluno, Aniversariante, AlunoRequest, Aula, AulaRequest, AulaAdiarResponse, AulaComplementarRequest, AulaComplementarResponse, Campanha, ChamadaResponse,
  Classe, ClasseRequest, DesafiosResponse, MinhaFrequenciaResponse, NotasProvaResponse, Professor, Prova, ProvaRequest,
  QuizQuestaoEdit, MinhaProva, QuizParaResponder, RespostaIn, ResultadoProva,
  DashboardResponse, RelatorioGeralResponse, RelatorioPresencaResponse, RelatorioVisitantesResponse,
  BoletimResponse, Auditoria, MeuRanking, RankingTurmasResponse, Requisicao, RequisicaoRequest, Usuario, UsuarioRequest,
  Visitante, VisitanteRequest, UsoResponse,
} from './models';

/** Cliente único para todos os endpoints da API. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // ---- Recuperação de senha (públicos, pré-login) ----
  esqueciSenha(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.api}/auth/esqueci-senha`, { email });
  }
  validarTokenReset(token: string): Observable<{ username: string }> {
    return this.http.get<{ username: string }>(`${this.api}/auth/redefinir/${encodeURIComponent(token)}`);
  }
  redefinirSenha(token: string, novaSenha: string): Observable<void> {
    return this.http.post<void>(`${this.api}/auth/redefinir`, { token, novaSenha });
  }

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
  listarProfessores(classeId: number): Observable<Professor[]> {
    return this.http.get<Professor[]>(`${this.api}/classes/${classeId}/professores`);
  }
  atualizarAula(id: number, a: AulaRequest): Observable<Aula> {
    return this.http.put<Aula>(`${this.api}/aulas/${id}`, a);
  }
  deletarAula(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/aulas/${id}`);
  }
  complementarAula(id: number, req: AulaComplementarRequest): Observable<AulaComplementarResponse> {
    return this.http.post<AulaComplementarResponse>(`${this.api}/aulas/${id}/complementar`, req);
  }
  adiarAula(id: number): Observable<AulaAdiarResponse> {
    return this.http.post<AulaAdiarResponse>(`${this.api}/aulas/${id}/adiar`, {});
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
  rankings(classeId?: number | null, ano?: number | null, trimestre?: number | null): Observable<DesafiosResponse> {
    let params = new HttpParams();
    if (classeId != null) { params = params.set('classeId', classeId); }
    if (ano != null && trimestre != null) {
      params = params.set('ano', ano).set('trimestre', trimestre);
    }
    return this.http.get<DesafiosResponse>(`${this.api}/desafios/rankings`, { params });
  }

  /** Ranking das turmas entre si (média por aluno). Compara todas as turmas do escopo — ignora o seletor de turma. */
  rankingTurmas(ano?: number | null, trimestre?: number | null): Observable<RankingTurmasResponse> {
    let params = new HttpParams();
    if (ano != null && trimestre != null) {
      params = params.set('ano', ano).set('trimestre', trimestre);
    }
    return this.http.get<RankingTurmasResponse>(`${this.api}/desafios/rankings-turmas`, { params });
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

  // ---------- Campanhas ----------
  listarCampanhas(): Observable<Campanha[]> {
    return this.http.get<Campanha[]>(`${this.api}/campanhas`);
  }
  criarCampanha(form: FormData): Observable<Campanha> {
    // multipart: campos (titulo, mensagem, classeId) + imagens[] (arte)
    return this.http.post<Campanha>(`${this.api}/campanhas`, form);
  }
  // ---------- Aluno (visão própria) ----------
  minhaFrequencia(): Observable<MinhaFrequenciaResponse> {
    return this.http.get<MinhaFrequenciaResponse>(`${this.api}/me/frequencia`);
  }
  meusAniversariantes(): Observable<Aniversariante[]> {
    return this.http.get<Aniversariante[]>(`${this.api}/me/aniversariantes`);
  }
  // ---------- Visitantes ----------
  listarVisitantes(aulaId: number): Observable<Visitante[]> {
    return this.http.get<Visitante[]>(`${this.api}/visitantes`, { params: new HttpParams().set('aulaId', aulaId) });
  }
  adicionarVisitante(aulaId: number, v: VisitanteRequest): Observable<Visitante> {
    return this.http.post<Visitante>(`${this.api}/visitantes`, v, { params: new HttpParams().set('aulaId', aulaId) });
  }
  removerVisitante(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/visitantes/${id}`);
  }

  // ---------- Relatório geral (dia, consolidado) ----------
  relatorioGeral(data?: string): Observable<RelatorioGeralResponse> {
    let params = new HttpParams();
    if (data) params = params.set('data', data);
    return this.http.get<RelatorioGeralResponse>(`${this.api}/relatorios/geral`, { params });
  }

  // ---------- Conta (próprio usuário) ----------
  trocarSenha(senhaAtual: string, novaSenha: string): Observable<void> {
    return this.http.put<void>(`${this.api}/me/senha`, { senhaAtual, novaSenha });
  }

  // ---------- Relatório de visitantes ----------
  relatorioVisitantes(inicio?: string, fim?: string, classeId?: number | null): Observable<RelatorioVisitantesResponse> {
    let params = new HttpParams();
    if (inicio) params = params.set('inicio', inicio);
    if (fim) params = params.set('fim', fim);
    if (classeId) params = params.set('classeId', classeId);
    return this.http.get<RelatorioVisitantesResponse>(`${this.api}/relatorios/visitantes`, { params });
  }

  // ---------- Boletim ----------
  boletim(alunoId: number, ano: number, trimestre: number): Observable<BoletimResponse> {
    const params = new HttpParams().set('alunoId', alunoId).set('ano', ano).set('trimestre', trimestre);
    return this.http.get<BoletimResponse>(`${this.api}/boletim`, { params });
  }
  meuRanking(): Observable<MeuRanking> {
    return this.http.get<MeuRanking>(`${this.api}/me/ranking`);
  }

  meuRankingTurmas(): Observable<RankingTurmasResponse> {
    return this.http.get<RankingTurmasResponse>(`${this.api}/me/ranking-turmas`);
  }

  // ---------- Tesouraria: requisições ----------
  listarRequisicoes(status?: string | null): Observable<Requisicao[]> {
    let params = new HttpParams();
    if (status) { params = params.set('status', status); }
    return this.http.get<Requisicao[]>(`${this.api}/requisicoes`, { params });
  }
  buscarRequisicao(id: number): Observable<Requisicao> {
    return this.http.get<Requisicao>(`${this.api}/requisicoes/${id}`);
  }
  criarRequisicao(r: RequisicaoRequest): Observable<Requisicao> {
    return this.http.post<Requisicao>(`${this.api}/requisicoes`, r);
  }
  aprovarRequisicao(id: number, valorAprovado: number | null, parecer: string | null, comprovante?: File | null): Observable<Requisicao> {
    const fd = new FormData();
    if (valorAprovado != null) { fd.append('valorAprovado', String(valorAprovado)); }
    if (parecer) { fd.append('parecer', parecer); }
    if (comprovante) { fd.append('comprovante', comprovante); }
    return this.http.post<Requisicao>(`${this.api}/requisicoes/${id}/aprovar`, fd);
  }
  negarRequisicao(id: number, parecer: string | null): Observable<Requisicao> {
    return this.http.post<Requisicao>(`${this.api}/requisicoes/${id}/negar`, { parecer });
  }
  cancelarRequisicao(id: number): Observable<Requisicao> {
    return this.http.post<Requisicao>(`${this.api}/requisicoes/${id}/cancelar`, {});
  }
  finalizarRequisicao(id: number, valorGasto: number | null, observacao: string | null, arquivos: File[], comprovanteTroco?: File | null): Observable<Requisicao> {
    const fd = new FormData();
    if (valorGasto != null) { fd.append('valorGasto', String(valorGasto)); }
    if (observacao) { fd.append('observacao', observacao); }
    arquivos.forEach((f) => fd.append('anexos', f));
    if (comprovanteTroco) { fd.append('comprovanteTroco', comprovanteTroco); }
    return this.http.post<Requisicao>(`${this.api}/requisicoes/${id}/finalizar`, fd);
  }
  baixarAnexo(id: number): Observable<Blob> {
    return this.http.get(`${this.api}/requisicoes/anexos/${id}`, { responseType: 'blob' });
  }
  meuBoletim(ano: number, trimestre: number): Observable<BoletimResponse> {
    const params = new HttpParams().set('ano', ano).set('trimestre', trimestre);
    return this.http.get<BoletimResponse>(`${this.api}/me/boletim`, { params });
  }

  // ---------- Notas: lançar e notificar ----------
  notificarNotas(provaId: number): Observable<{ enviados: number }> {
    return this.http.post<{ enviados: number }>(`${this.api}/provas/${provaId}/notas/notificar`, {});
  }

  // ---------- Quiz (questões da prova online) ----------
  obterQuestoesProva(provaId: number): Observable<QuizQuestaoEdit[]> {
    return this.http.get<QuizQuestaoEdit[]>(`${this.api}/provas/${provaId}/questoes`);
  }
  salvarQuestoesProva(provaId: number, questoes: QuizQuestaoEdit[]): Observable<void> {
    return this.http.put<void>(`${this.api}/provas/${provaId}/questoes`, { questoes });
  }

  // ---------- Aluno: minhas provas online ----------
  minhasProvas(): Observable<MinhaProva[]> {
    return this.http.get<MinhaProva[]>(`${this.api}/me/provas`);
  }
  obterProvaParaResponder(provaId: number): Observable<QuizParaResponder> {
    return this.http.get<QuizParaResponder>(`${this.api}/me/provas/${provaId}`);
  }
  submeterProva(provaId: number, respostas: RespostaIn[]): Observable<ResultadoProva> {
    return this.http.post<ResultadoProva>(`${this.api}/me/provas/${provaId}/submeter`, { respostas });
  }
  obterResultadoProva(provaId: number): Observable<ResultadoProva> {
    return this.http.get<ResultadoProva>(`${this.api}/me/provas/${provaId}/resultado`);
  }

  // ---------- Dashboard ----------
  dashboard(classeId?: number | null): Observable<DashboardResponse> {
    let params = new HttpParams();
    if (classeId) params = params.set('classeId', classeId);
    return this.http.get<DashboardResponse>(`${this.api}/dashboard`, { params });
  }

  // ---------- Auditoria (admin) ----------
  listarAuditoria(entidade?: string | null, inicio?: string | null, fim?: string | null): Observable<Auditoria[]> {
    let params = new HttpParams();
    if (entidade) { params = params.set('entidade', entidade); }
    if (inicio) { params = params.set('inicio', inicio); }
    if (fim) { params = params.set('fim', fim); }
    return this.http.get<Auditoria[]>(`${this.api}/auditoria`, { params });
  }

  // ---------- Uso (estatísticas de engajamento) ----------
  uso(): Observable<UsoResponse> {
    return this.http.get<UsoResponse>(`${this.api}/uso`);
  }
  ping(): Observable<void> {
    return this.http.put<void>(`${this.api}/me/ping`, {});
  }
  evento(recurso: string, acao: 'ABRIR' | 'CLICAR' = 'ABRIR'): Observable<void> {
    return this.http.post<void>(`${this.api}/me/evento`, { recurso, acao });
  }
}
