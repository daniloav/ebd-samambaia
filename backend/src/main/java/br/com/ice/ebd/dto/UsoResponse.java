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
        List<Contagem> dispositivos) {

    public record UsuarioAtivo(String username, String papel, LocalDateTime ultimoAcesso) {}

    public record PontoDia(LocalDate data, long acessos, long ativos) {}

    public record TopUsuario(String username, String papel, long acessos, LocalDateTime ultimoAcesso) {}

    public record Contagem(String rotulo, long quantidade) {}
}
