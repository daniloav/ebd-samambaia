export type Role = 'ADMIN' | 'PROFESSOR' | 'ALUNO';

export interface LoginResponse {
  token: string;
  username: string;
  role: Role;
  expiresInSeconds: number;
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
}

export interface AlunoRequest {
  nome: string;
  classeId?: number;
  telefone?: string | null;
  dataNascimento?: string | null;
  email?: string | null;
  recebeNotificacoes?: boolean;
  ativo?: boolean;
}

export interface Aula {
  id: number;
  data: string;
  tema?: string | null;
  classeId?: number;
  classeNome?: string;
}

export interface AulaRequest {
  classeId?: number;
  data: string;
  tema?: string | null;
}

export interface PresencaItem {
  alunoId: number;
  alunoNome: string;
  presente: boolean;
  trouxeBiblia: boolean;
  trouxeRevista: boolean;
  estudouLicao: boolean;
}

export interface ChamadaResponse {
  aulaId: number;
  data: string;
  tema?: string | null;
  itens: PresencaItem[];
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
  role: Role;
  ativo: boolean;
  alunoId?: number | null;
  alunoNome?: string | null;
  email?: string | null;
  classes?: { id: number; nome: string }[];
}

export interface UsuarioRequest {
  username: string;
  senha?: string | null;
  role: Role;
  alunoId?: number | null;
  classeIds?: number[] | null;
  email?: string | null;
  ativo?: boolean;
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
  data: string;
  tema?: string | null;
  presente: boolean;
  trouxeBiblia: boolean;
  trouxeRevista: boolean;
  estudouLicao: boolean;
}

export interface MinhaFrequenciaResponse {
  alunoNome: string;
  totalAulas: number;
  presencas: number;
  faltas: number;
  percentualPresenca: number;
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
  situacao: string;
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
