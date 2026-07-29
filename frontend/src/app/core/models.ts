export type Role = 'ADMIN' | 'PROFESSOR' | 'ALUNO';
export type Perfil = 'GESTAO' | 'ALUNO';

export interface LoginResponse {
  token: string;
  username: string;
  expiresInSeconds: number;
  precisaTrocarSenha: boolean;
  ehAdmin: boolean;
  ehProfessor: boolean;
  ehAluno: boolean;
  ehTesoureiro: boolean;
  ehLider: boolean;
}

export interface Aluno {
  id: number;
  nome: string;
  telefone?: string | null;
  dataNascimento?: string | null;
  ativo: boolean;
  dataCadastro?: string;
  classeId: number;
  classeNome?: string;
  email?: string | null;
  recebeNotificacoes?: boolean;
  login?: string | null;
}

export interface AlunoRequest {
  nome: string;
  classeId?: number;
  telefone?: string | null;
  dataNascimento?: string | null;
  email?: string | null;
  login?: string | null;
  recebeNotificacoes?: boolean;
  ativo?: boolean;
}

export interface Aula {
  id: number;
  data: string;
  tema?: string | null;
  classeId?: number;
  classeNome?: string;
  professorId?: number | null;
  professorNome?: string | null;
  professorAlunoId?: number | null;
}

export interface Professor {
  id: number;
  nome: string;
  alunoId?: number | null;
}

export interface AulaRequest {
  classeId?: number;
  data: string;
  tema?: string | null;
  professorId?: number | null;
}

export interface PresencaItem {
  alunoId: number;
  alunoNome: string;
  presente: boolean;
  trouxeBiblia: boolean;
  trouxeRevista: boolean;
  estudouLicao: boolean;
  professorDaAula?: boolean;
  justificada?: boolean;
  justificativaMotivo?: string | null;
}

export interface ChamadaResponse {
  aulaId: number;
  data: string;
  tema?: string | null;
  itens: PresencaItem[];
  emailsEnviados?: number | null;
  alertas?: string[];
}

export type TipoProva = 'OFFLINE' | 'ONLINE';
export type TipoQuestao = 'MULTIPLA' | 'VF';

export interface Prova {
  id: number;
  titulo: string;
  data: string;
  notaMaxima: number;
  classeId?: number;
  classeNome?: string;
  tipo?: TipoProva;
  abreEm?: string | null;
  fechaEm?: string | null;
  numQuestoes?: number;
}

export interface ProvaRequest {
  classeId?: number;
  titulo: string;
  data: string;
  notaMaxima: number;
  tipo?: TipoProva;
  abreEm?: string | null;
  fechaEm?: string | null;
}

export interface QuizAlternativaEdit {
  id?: number;
  texto: string;
  correta: boolean;
}
export interface QuizQuestaoEdit {
  id?: number;
  enunciado: string;
  tipo: TipoQuestao;
  pontos: number;
  alternativas: QuizAlternativaEdit[];
}

// ---- Visão do aluno (provas ONLINE) ----
export type StatusProva = 'FUTURA' | 'DISPONIVEL' | 'RESPONDIDA' | 'FECHADA';

export interface MinhaProva {
  id: number;
  titulo: string;
  data: string;
  notaMaxima: number;
  numQuestoes: number;
  status: StatusProva;
  abreEm: string | null;
  fechaEm: string | null;
  nota: number | null;
}

export interface AlternativaResponder {
  id: number;
  texto: string;
}
export interface QuestaoResponder {
  id: number;
  enunciado: string;
  tipo: TipoQuestao;
  pontos: number;
  alternativas: AlternativaResponder[];
}
export interface QuizParaResponder {
  provaId: number;
  titulo: string;
  notaMaxima: number;
  questoes: QuestaoResponder[];
}

export interface RespostaIn {
  questaoId: number;
  alternativaId: number | null;
}
export interface ResultadoQuestao {
  questaoId: number;
  enunciado: string;
  escolhidaId: number | null;
  corretaId: number | null;
  acertou: boolean;
  pontos: number;
  alternativas: AlternativaResponder[];
}
export interface ResultadoProva {
  titulo: string;
  nota: number;
  notaMaxima: number;
  acertos: number;
  total: number;
  questoes: ResultadoQuestao[];
}

export interface NotaItem {
  alunoId: number;
  alunoNome: string;
  nota: number | null;
}

export interface NotasProvaResponse {
  provaId: number;
  titulo: string;
  data: string;
  notaMaxima: number;
  /** true = a grade lista só os alunos presentes na aula da data (prova offline). */
  somentePresentes?: boolean;
  itens: NotaItem[];
}

export interface RelatorioPresencaItem {
  alunoId: number;
  nome: string;
  totalAulas: number;
  presencas: number;
  faltas: number;
  percentualPresenca: number;
  trouxeBiblia: number;
  trouxeRevista: number;
  estudouLicao: number;
  trouxeVisitante: number;
}

export interface RelatorioPresencaResponse {
  inicio: string;
  fim: string;
  totalAulas: number;
  itens: RelatorioPresencaItem[];
}

export interface RankingItem {
  posicao: number;
  alunoId: number;
  nome: string;
  valor: number;
  detalhe: string;
}

export interface DesafiosResponse {
  totalAulas: number;
  totalProvas: number;
  menosFaltou: RankingItem[];
  maisTrouxeBiblia: RankingItem[];
  maisTrouxeRevista: RankingItem[];
  maisEstudouLicao: RankingItem[];
  maisTrouxeVisitante: RankingItem[];
  melhoresNotas: RankingItem[];
  classificacaoGeral: RankingItem[];
}

export interface Classe {
  id: number;
  nome: string;
  descricao?: string | null;
  ativo: boolean;
}

export interface ClasseRequest {
  nome: string;
  descricao?: string | null;
  ativo?: boolean;
}

export interface Usuario {
  id: number;
  username: string;
  ehAdmin: boolean;
  ehProfessor: boolean;
  ehAluno: boolean;
  ativo: boolean;
  alunoId?: number | null;
  alunoNome?: string | null;
  email?: string | null;
  ehTesoureiro?: boolean;
  ehLider?: boolean;
  classes?: { id: number; nome: string }[];
}

export interface UsuarioRequest {
  username: string;
  senha?: string | null;
  ehAdmin?: boolean;
  ehProfessor?: boolean;
  ehAluno?: boolean;
  alunoId?: number | null;
  classeIds?: number[] | null;
  email?: string | null;
  ativo?: boolean;
  ehTesoureiro?: boolean;
  ehLider?: boolean;
}

export interface CampanhaImagemMeta {
  id: number;
  nome: string | null;
  tipo: string;
}
export interface Campanha {
  id: number;
  titulo: string;
  mensagem: string;
  classeId?: number | null;
  classeNome?: string;
  totalEnviados: number;
  criadoPor?: string | null;
  dataEnvio: string;
  imagens?: CampanhaImagemMeta[];
}

export interface CampanhaRequest {
  titulo: string;
  mensagem: string;
  classeId?: number | null;
}

export interface MinhaFrequenciaItem {
  aulaId: number;
  data: string;
  tema?: string | null;
  presente: boolean;
  trouxeBiblia: boolean;
  trouxeRevista: boolean;
  estudouLicao: boolean;
  justificada: boolean;
  justificativaMotivo?: string | null;
}

export interface MinhaFrequenciaResponse {
  alunoNome: string;
  totalAulas: number;
  presencas: number;
  faltas: number;
  percentualPresenca: number;
  faltasJustificadas: number;
  itens: MinhaFrequenciaItem[];
}

export interface Visitante {
  id: number;
  nome: string;
  email?: string | null;
  telefone?: string | null;
  trazidoPorId?: number | null;
  trazidoPorNome?: string | null;
  dataCadastro: string;
  alerta?: string | null;
}

export interface VisitanteRequest {
  nome: string;
  email?: string | null;
  telefone?: string | null;
  trazidoPorAlunoId?: number | null;
}

export interface RelatorioGeralLinha {
  classeId: number;
  classeNome: string;
  tema?: string | null;
  presentes: number; faltosos: number; biblias: number; revistas: number; licoes: number; visitantes: number;
}

export interface RelatorioGeralTotais {
  presentes: number; faltosos: number; biblias: number; revistas: number; licoes: number; visitantes: number;
}

export interface RelatorioGeralResponse {
  data: string;
  totalTurmas: number;
  totais: RelatorioGeralTotais;
  turmas: RelatorioGeralLinha[];
}

export interface RelatorioVisitantesItem {
  id: number;
  nome: string;
  email: string | null;
  telefone: string | null;
  turma: string;
  dataAula: string;
  trazidoPorNome: string | null;
}
export interface RelatorioVisitantesResponse {
  inicio: string;
  fim: string;
  classeId: number | null;
  classeNome: string | null;
  total: number;
  itens: RelatorioVisitantesItem[];
}

export interface BoletimProvaItem {
  titulo: string;
  data: string;
  nota: number | null;
  notaMaxima: number;
  percentual: number | null;
}
export interface BoletimFrequencia {
  totalAulas: number;
  presencas: number;
  faltas: number;
  percentualPresenca: number;
  biblias: number;
  revistas: number;
  licoes: number;
}
export interface BoletimResponse {
  alunoId: number;
  alunoNome: string;
  turma: string;
  ano: number;
  trimestre: number;
  periodoInicio: string;
  periodoFim: string;
  provas: BoletimProvaItem[];
  mediaNotas: number;
  aproveitamentoPct: number;
  frequencia: BoletimFrequencia;
  visitantesTrazidos: number;
}

export interface DashPontoFrequencia {
  data: string;
  tema: string | null;
  presentes: number;
  total: number;
  pct: number;
}
export interface DashDistribuicao {
  excelente: number;
  boa: number;
  atencao: number;
}
export interface DashboardResponse {
  totalAlunos: number;
  totalAulas: number;
  totalProvas: number;
  presencaMediaPct: number;
  frequenciaPorAula: DashPontoFrequencia[];
  distribuicao: DashDistribuicao;
}

// ---- Auditoria de ações (admin) ----
export type AcaoAuditoria = 'CRIAR' | 'ATUALIZAR' | 'EXCLUIR';
export type EntidadeAuditoria = 'ALUNO' | 'AULA' | 'PROVA' | 'USUARIO';
export interface Auditoria {
  id: number;
  dataHora: string;
  usuario: string;
  acao: AcaoAuditoria;
  entidade: EntidadeAuditoria;
  entidadeId: number | null;
  descricao: string | null;
}

// ---- Ranking resumido do aluno ----
export interface RankingResumoItem {
  posicao: number;
  alunoId: number;
  nome: string;
  valor: number;
  detalhe: string;
  eu: boolean;
}
export interface MeuRanking {
  turmaNome: string;
  totalParticipantes: number;
  podio: RankingResumoItem[];
  minhaPosicao: RankingResumoItem | null;
}

// ---- Ranking por turma (desafio entre classes) ----
export interface RankingTurmaItem {
  posicao: number;
  classeId: number;
  turmaNome: string;
  valor: number;   // média de pontos por aluno
  total: number;   // soma bruta de pontos da turma
  alunos: number;
  detalhe: string;
}
export interface RankingTurmasResponse {
  totalAulas: number;
  totalProvas: number;
  minhaClasseId: number | null;
  turmas: RankingTurmaItem[];
}

// ---- Tesouraria: requisições ----
export type StatusRequisicao = 'ABERTA' | 'APROVADA' | 'NEGADA' | 'FINALIZADA' | 'CANCELADA';
export type FormaRepasse = 'DINHEIRO' | 'PIX';
export type TipoChavePix = 'CPF' | 'EMAIL' | 'TELEFONE';
export type CategoriaAnexo = 'NOTA_FISCAL' | 'COMPROVANTE' | 'TROCO';
export interface RequisicaoAnexoResumo { id: number; nome?: string | null; tipo: string; categoria: CategoriaAnexo; }
export interface Requisicao {
  id: number;
  numero: string;
  status: StatusRequisicao;
  solicitanteId: number;
  solicitanteNome: string;
  ministerio: string;
  nomeEvento?: string | null;
  destinacao: string;
  motivo: string;
  valorSolicitado: number;
  dataNecessidade?: string | null;
  valorAprovado?: number | null;
  parecerTesoureiro?: string | null;
  avaliadoPorNome?: string | null;
  avaliadoEm?: string | null;
  valorGasto?: number | null;
  observacaoFinal?: string | null;
  finalizadoEm?: string | null;
  criadoEm: string;
  possuiComprovante?: boolean;
  formaRepasse: FormaRepasse;
  pixTipo?: TipoChavePix | null;
  pixChave?: string | null;
  anexos: RequisicaoAnexoResumo[];
}
export interface RequisicaoRequest {
  ministerio: string;
  nomeEvento?: string | null;
  destinacao: string;
  motivo: string;
  valorSolicitado: number;
  dataNecessidade?: string | null;
  formaRepasse?: FormaRepasse;
  pixTipo?: TipoChavePix | null;
  pixChave?: string | null;
}
