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
  trouxeVisitante: boolean;
}

export interface ChamadaResponse {
  aulaId: number;
  data: string;
  tema?: string | null;
  itens: PresencaItem[];
}

export interface Prova {
  id: number;
  titulo: string;
  data: string;
  notaMaxima: number;
  classeId?: number;
  classeNome?: string;
}

export interface ProvaRequest {
  classeId?: number;
  titulo: string;
  data: string;
  notaMaxima: number;
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
}

export interface UsuarioRequest {
  username: string;
  senha?: string | null;
  role: Role;
  alunoId?: number | null;
  ativo?: boolean;
}
