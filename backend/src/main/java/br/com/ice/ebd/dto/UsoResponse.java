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
        List<CoberturaTurma> coberturaTurmas,   // chamada da última aula prevista de cada turma
        // A) Tempo real (lote 3)
        long picoHoje, long pico30d,            // máx. atividade simultânea (janela de 15 min)
        long aoVivoNaAula, LocalDate aoVivoData, // ativos no horário da EBD do último domingo
        // C) Funil + coorte (lote 3)
        List<EtapaFunil> funil,                 // cadastrado → 1º acesso → trocou senha → usou função
        List<Coorte> coortes,                   // por mês de cadastro (últimos 6)
        // E) Engajamento do aluno (lote 3)
        List<StreakUsuario> streaks,            // semanas seguidas com atividade (top alunos)
        double pctForaDoDomingo,                // % da atividade (30d) fora do domingo
        // G) Técnico (lote 3)
        List<Contagem> plataformas,             // celular × computador (30 dias)
        List<Contagem> versoesSistema) {        // versão de SO/navegador (30 dias)

    public record UsuarioAtivo(String username, String papel, LocalDateTime ultimoAcesso) {}

    public record PontoDia(LocalDate data, long acessos, long ativos) {}

    public record TopUsuario(String username, String papel, long acessos, LocalDateTime ultimoAcesso) {}

    public record Contagem(String rotulo, long quantidade) {}

    /** Chamadas lançadas no prazo (no dia da aula) × atrasadas × sem data (histórico sem carimbo). */
    public record ChamadaPrazo(long noPrazo, long atrasadas, long semData, double pctNoPrazo) {}

    /**
     * Situação da chamada da <b>última aula prevista</b> de uma turma: já registrada (FEITA),
     * a aula já ocorreu e ninguém lançou (PENDENTE) ou não há aula elegível (SEM_AULA — turma
     * sem agenda até hoje, ou só com aulas adiadas; não é cobrança).
     */
    public enum SituacaoCobertura { FEITA, PENDENTE, SEM_AULA }

    /** Cobertura da chamada de uma turma: situação e a data da aula avaliada (nula em SEM_AULA). */
    public record CoberturaTurma(String turma, SituacaoCobertura situacao, LocalDate aulaData) {}

    /** Uma etapa do funil de ativação (rótulo + quantos usuários chegaram nela). */
    public record EtapaFunil(String rotulo, long quantidade) {}

    /** Coorte por mês de cadastro: quantos entraram, quantos ativaram e quantos seguem ativos (30d). */
    public record Coorte(String rotulo, long cadastrados, long ativados, long ativos) {}

    /** Sequência de semanas seguidas com atividade de um aluno (engajamento contínuo). */
    public record StreakUsuario(String username, String papel, int semanas) {}
}
