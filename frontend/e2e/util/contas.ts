/**
 * Contas semeadas pelo `DataInitializer` no 1º boot do backend.
 * Mantê-las em um só lugar evita espalhar credenciais pelos testes.
 */
export const CONTAS = {
  admin: { usuario: 'admin', senha: 'admin123' },
  professor: { usuario: 'professor', senha: 'prof123' },
  // 1º aluno de exemplo ("Ana Beatriz Souza") → login = primeiro.ultimo nome.
  // Senha padrão do 1º acesso; cai em /conta (troca obrigatória de senha).
  aluno: { usuario: 'ana.souza', senha: '12345678' },
} as const;
