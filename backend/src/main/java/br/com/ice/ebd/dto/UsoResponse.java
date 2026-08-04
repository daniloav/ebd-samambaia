package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Painel de estatísticas de uso (engajamento) — só ADMIN. Quick win dos itens A–G do
 * roadmap: online agora, volume de acessos, DAU/WAU/MAU, taxa de ativação, série diária,
 * distribuição por hora/dia, mais ativos, dormentes e dispositivos.
 */
public record UsoResponse(
        // A) Tempo real
        long onlineAgora,
        List<UsuarioAtivo> online,
        // B) Volume de acesso (logins)
        long acessosHoje, long acessos7d, long acessos30d,
        // B) Usuários únicos ativos (DAU / WAU / MAU)
        long ativosHoje, long ativos7d, long ativos30d,
        // C) Adoção / ativação
        long totalUsuarios, long usuariosComAcesso, long usuariosNuncaAcessaram, double taxaAtivacaoPct,
        long alunosTotal, long alunosAtivados, double taxaAtivacaoAlunosPct,
        // B) Séries
        List<PontoDia> serieDiaria,      // últimos 14 dias
        List<Long> porHora,              // 24 posições (0..23), últimos 30 dias
        List<Long> porDiaSemana,         // 7 posições (0=Dom..6=Sáb), últimos 30 dias
        // E/F) Rankings de engajamento
        List<TopUsuario> maisAtivos,     // top 10 por nº de acessos (30 dias)
        List<TopUsuario> dormentes,      // com login, mas sem acesso há 14+ dias
        // G) Dispositivos
        List<Contagem> dispositivos,
        // D) Uso por funcionalidade (lote 2)
        List<Contagem> featuresMaisUsadas,   // telas mais abertas (30 dias)
        List<Contagem> acoesNotaveis,        // cliques instrumentados (30 dias)
        // F) Professores / gestão (lote 2)
        List<TopUsuario> professoresMaisAtivos, // por nº de ações na auditoria (30 dias)
        ChamadaPrazo chamadaPrazo,              // chamadas no prazo × atrasadas
        List<CoberturaTurma> coberturaTurmas) { // turmas com/sem chamada na semana atual

    public record UsuarioAtivo(String username, String papel, LocalDateTime ultimoAcesso) {}

    public record PontoDia(LocalDate data, long acessos, long ativos) {}

    public record TopUsuario(String username, String papel, long acessos, LocalDateTime ultimoAcesso) {}

    public record Contagem(String rotulo, long quantidade) {}

    /** Chamadas lançadas no prazo (no dia da aula) × atrasadas × sem data (histórico sem carimbo). */
    public record ChamadaPrazo(long noPrazo, long atrasadas, long semData, double pctNoPrazo) {}

    /** Cobertura de uma turma na semana corrente: se já teve chamada e em qual aula. */
    public record CoberturaTurma(String turma, boolean cobriu, LocalDate aulaData) {}
}
