package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.UsoResponse;
import br.com.ice.ebd.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Agrega as estatísticas de uso do painel /uso (ADMIN) a partir de acesso_evento + usuario. */
@ApplicationScoped
public class UsoService {

    /** Janela do "online agora": last-seen dentro deste intervalo (o front dá ping a cada ~60s). */
    private static final int ONLINE_MINUTOS = 15;
    private static final int DIAS_SERIE = 14;
    private static final int DORMENTE_DIAS = 14;
    private static final int TOP_LIMITE = 10;

    // SQL nativo em constantes literais (sem interpolação de variável) — extract(hour|dow ...).
    private static final String SQL_ACESSOS_POR_HORA =
            "select cast(extract(hour from data_hora) as int) b, count(*) from acesso_evento where data_hora >= :desde group by b";
    private static final String SQL_ACESSOS_POR_DOW =
            "select cast(extract(dow from data_hora) as int) b, count(*) from acesso_evento where data_hora >= :desde group by b";
    // Chamada no prazo × atrasada: agrega por aula o 1º registro da chamada vs. a data da aula.
    private static final String SQL_CHAMADA_PRAZO =
            "select "
            + "  coalesce(sum(case when dt is not null and dt <= a_data then 1 else 0 end), 0), "
            + "  coalesce(sum(case when dt is not null and dt >  a_data then 1 else 0 end), 0), "
            + "  coalesce(sum(case when dt is null then 1 else 0 end), 0) "
            + "from ("
            + "  select p.aula_id, cast(min(p.registrada_em) as date) dt, max(a.data) a_data "
            + "  from presenca p join aula a on a.id = p.aula_id group by p.aula_id"
            + ") t";

    @Inject EntityManager em;
    @Inject br.com.ice.ebd.repository.UsuarioRepository usuarioRepository;

    public UsoResponse gerar() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioHoje = LocalDate.now().atStartOfDay();
        LocalDateTime ha7 = agora.minusDays(7);
        LocalDateTime ha30 = agora.minusDays(30);
        LocalDateTime onlineDesde = agora.minusMinutes(ONLINE_MINUTOS);
        LocalDateTime dormenteAntes = agora.minusDays(DORMENTE_DIAS);

        // ---- A) Online agora (last-seen recente) ----
        List<Usuario> onlineUsuarios = em.createQuery(
                        "select u from Usuario u where u.ultimoAcesso >= :desde order by u.ultimoAcesso desc", Usuario.class)
                .setParameter("desde", onlineDesde).getResultList();
        List<UsoResponse.UsuarioAtivo> online = new ArrayList<>();
        for (Usuario u : onlineUsuarios) {
            online.add(new UsoResponse.UsuarioAtivo(u.getUsername(), papel(u), u.getUltimoAcesso()));
        }

        // ---- B) Volume de acessos (logins) ----
        long acessosHoje = contarEventos(inicioHoje);
        long acessos7d = contarEventos(ha7);
        long acessos30d = contarEventos(ha30);

        // ---- B) Usuários únicos ativos (DAU/WAU/MAU) ----
        long ativosHoje = contarAtivos(inicioHoje);
        long ativos7d = contarAtivos(ha7);
        long ativos30d = contarAtivos(ha30);

        // ---- C) Adoção / ativação ----
        long totalUsuarios = scalar("select count(u) from Usuario u where u.ativo = true");
        long comAcesso = scalar("select count(u) from Usuario u where u.ativo = true and u.ultimoAcesso is not null");
        long nuncaAcessaram = totalUsuarios - comAcesso;
        double taxaAtivacao = pct(comAcesso, totalUsuarios);

        long alunosTotal = scalar("select count(u) from Usuario u where u.ativo = true and u.ehAluno = true");
        long alunosAtivados = scalar("select count(u) from Usuario u where u.ativo = true and u.ehAluno = true "
                + "and u.ultimoAcesso is not null and u.precisaTrocarSenha = false");
        double taxaAtivacaoAlunos = pct(alunosAtivados, alunosTotal);

        // ---- B) Séries ----
        List<UsoResponse.PontoDia> serieDiaria = serieDiaria(agora.minusDays(DIAS_SERIE - 1L));
        List<Long> porHora = distribuicao(ha30, SQL_ACESSOS_POR_HORA, 24);
        List<Long> porDiaSemana = distribuicao(ha30, SQL_ACESSOS_POR_DOW, 7);

        // ---- E/F) Mais ativos (30 dias) ----
        List<UsoResponse.TopUsuario> maisAtivos = new ArrayList<>();
        List<Object[]> topRows = em.createQuery(
                        "select a.usuario, count(a) from AcessoEvento a where a.dataHora >= :desde "
                        + "group by a.usuario order by count(a) desc", Object[].class)
                .setParameter("desde", ha30).setMaxResults(TOP_LIMITE).getResultList();
        for (Object[] r : topRows) {
            Usuario u = (Usuario) r[0];
            long qtd = ((Number) r[1]).longValue();
            maisAtivos.add(new UsoResponse.TopUsuario(u.getUsername(), papel(u), qtd, u.getUltimoAcesso()));
        }

        // ---- E) Dormentes (já acessaram, mas sumiram) ----
        List<UsoResponse.TopUsuario> dormentes = new ArrayList<>();
        List<Usuario> dormentesRows = em.createQuery(
                        "select u from Usuario u where u.ativo = true and u.ultimoAcesso is not null "
                        + "and u.ultimoAcesso < :antes order by u.ultimoAcesso asc", Usuario.class)
                .setParameter("antes", dormenteAntes).setMaxResults(TOP_LIMITE).getResultList();
        for (Usuario u : dormentesRows) {
            dormentes.add(new UsoResponse.TopUsuario(u.getUsername(), papel(u), 0, u.getUltimoAcesso()));
        }

        // ---- G) Dispositivos ----
        List<UsoResponse.Contagem> dispositivos = dispositivos(ha30);

        return new UsoResponse(
                online.size(), online,
                acessosHoje, acessos7d, acessos30d,
                ativosHoje, ativos7d, ativos30d,
                totalUsuarios, comAcesso, nuncaAcessaram, taxaAtivacao,
                alunosTotal, alunosAtivados, taxaAtivacaoAlunos,
                serieDiaria, porHora, porDiaSemana,
                maisAtivos, dormentes, dispositivos,
                // D) Uso por funcionalidade
                featuresMaisUsadas(ha30), acoesNotaveis(ha30),
                // F) Professores / gestão
                professoresMaisAtivos(ha30), chamadaPrazo(), coberturaTurmas());
    }

    // ============================ D) Uso por funcionalidade ============================

    /** Telas mais abertas nos últimos 30 dias (uso_evento com acao ABRIR), agregado por recurso. */
    private List<UsoResponse.Contagem> featuresMaisUsadas(LocalDateTime desde) {
        return contagemUsoEvento(desde, "ABRIR");
    }

    /** Cliques notáveis nos últimos 30 dias (uso_evento com acao CLICAR), agregado por recurso. */
    private List<UsoResponse.Contagem> acoesNotaveis(LocalDateTime desde) {
        return contagemUsoEvento(desde, "CLICAR");
    }

    private List<UsoResponse.Contagem> contagemUsoEvento(LocalDateTime desde, String acao) {
        List<Object[]> linhas = em.createQuery(
                        "select e.recurso, count(e) from UsoEvento e "
                        + "where e.dataHora >= :desde and e.acao = :acao "
                        + "group by e.recurso order by count(e) desc", Object[].class)
                .setParameter("desde", desde)
                .setParameter("acao", br.com.ice.ebd.model.UsoEvento.Acao.valueOf(acao))
                .getResultList();
        List<UsoResponse.Contagem> lista = new ArrayList<>();
        for (Object[] r : linhas) {
            lista.add(new UsoResponse.Contagem((String) r[0], ((Number) r[1]).longValue()));
        }
        return lista;
    }

    // ============================ F) Professores / gestão ============================

    /**
     * Usuários que mais agiram na gestão nos últimos 30 dias (nº de registros na auditoria —
     * criar/atualizar/excluir aluno, aula, prova, usuário). O username da auditoria é casado
     * com Usuario para exibir o papel; quem não é mais encontrado aparece como "—".
     */
    private List<UsoResponse.TopUsuario> professoresMaisAtivos(LocalDateTime desde) {
        List<Object[]> linhas = em.createQuery(
                        "select a.usuario, count(a) from Auditoria a where a.dataHora >= :desde "
                        + "group by a.usuario order by count(a) desc", Object[].class)
                .setParameter("desde", desde).setMaxResults(TOP_LIMITE).getResultList();
        List<UsoResponse.TopUsuario> lista = new ArrayList<>();
        for (Object[] r : linhas) {
            String username = (String) r[0];
            long qtd = ((Number) r[1]).longValue();
            Usuario u = usuarioRepository.findByUsername(username).orElse(null);
            String papel = u != null ? papel(u) : "—";
            LocalDateTime ultimo = u != null ? u.getUltimoAcesso() : null;
            lista.add(new UsoResponse.TopUsuario(username, papel, qtd, ultimo));
        }
        return lista;
    }

    /**
     * Chamadas no prazo × atrasadas: por aula (que tenha presença), compara a data do 1º
     * registro da chamada (min registrada_em) com a data da aula. No prazo = registrada até
     * o dia da aula; atrasada = depois; sem data = presenças antigas sem carimbo (pré-V27).
     */
    private UsoResponse.ChamadaPrazo chamadaPrazo() {
        Object[] r = (Object[]) em.createNativeQuery(SQL_CHAMADA_PRAZO).getSingleResult();
        long noPrazo = ((Number) r[0]).longValue();
        long atrasadas = ((Number) r[1]).longValue();
        long semData = ((Number) r[2]).longValue();
        long comData = noPrazo + atrasadas;
        double pct = comData > 0 ? Math.round(noPrazo * 10000.0 / comData) / 100.0 : 0.0;
        return new UsoResponse.ChamadaPrazo(noPrazo, atrasadas, semData, pct);
    }

    /**
     * Cobertura da semana corrente (segunda a domingo): para cada turma ativa, se já houve
     * chamada (aula com presença) numa aula desta semana e em qual data.
     */
    private List<UsoResponse.CoberturaTurma> coberturaTurmas() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioSemana = hoje.with(java.time.DayOfWeek.MONDAY);
        LocalDate fimSemana = inicioSemana.plusDays(6);

        // turma_id -> data da aula desta semana que já teve chamada (a mais recente)
        Map<Long, LocalDate> cobertas = new java.util.HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                        "select a.classe_id, max(a.data) from aula a "
                        + "where a.data between :ini and :fim "
                        + "and exists (select 1 from presenca p where p.aula_id = a.id) "
                        + "group by a.classe_id")
                .setParameter("ini", inicioSemana).setParameter("fim", fimSemana).getResultList();
        for (Object[] l : linhas) {
            Long turmaId = ((Number) l[0]).longValue();
            LocalDate data = ((java.sql.Date) l[1]).toLocalDate();
            cobertas.put(turmaId, data);
        }

        List<br.com.ice.ebd.model.Classe> turmas = em.createQuery(
                        "select c from Classe c where c.ativo = true order by c.nome", br.com.ice.ebd.model.Classe.class)
                .getResultList();
        List<UsoResponse.CoberturaTurma> lista = new ArrayList<>();
        for (br.com.ice.ebd.model.Classe c : turmas) {
            LocalDate data = cobertas.get(c.getId());
            lista.add(new UsoResponse.CoberturaTurma(c.getNome(), data != null, data));
        }
        return lista;
    }

    private long contarEventos(LocalDateTime desde) {
        return scalarParam("select count(a) from AcessoEvento a where a.dataHora >= :desde", desde);
    }

    private long contarAtivos(LocalDateTime desde) {
        return scalarParam("select count(distinct a.usuario.id) from AcessoEvento a where a.dataHora >= :desde", desde);
    }

    /** Série diária contínua (preenche dias sem acesso com zero). */
    private List<UsoResponse.PontoDia> serieDiaria(LocalDateTime desde) {
        Map<LocalDate, long[]> porDia = new TreeMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                        "select cast(data_hora as date) d, count(*), count(distinct usuario_id) "
                        + "from acesso_evento where data_hora >= :desde group by d")
                .setParameter("desde", desde).getResultList();
        for (Object[] l : linhas) {
            LocalDate d = ((java.sql.Date) l[0]).toLocalDate();
            porDia.put(d, new long[]{((Number) l[1]).longValue(), ((Number) l[2]).longValue()});
        }
        List<UsoResponse.PontoDia> serie = new ArrayList<>();
        LocalDate dia = desde.toLocalDate();
        LocalDate hoje = LocalDate.now();
        while (!dia.isAfter(hoje)) {
            long[] v = porDia.getOrDefault(dia, new long[]{0, 0});
            serie.add(new UsoResponse.PontoDia(dia, v[0], v[1]));
            dia = dia.plusDays(1);
        }
        return serie;
    }

    /** Distribuição de acessos por bucket (0..23 p/ hora; 0=Dom..6=Sáb p/ dia da semana). */
    private List<Long> distribuicao(LocalDateTime desde, String sqlLiteral, int tamanho) {
        long[] buckets = new long[tamanho];
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(sqlLiteral)
                .setParameter("desde", desde).getResultList();
        for (Object[] l : linhas) {
            int b = ((Number) l[0]).intValue();
            if (b >= 0 && b < tamanho) {
                buckets[b] = ((Number) l[1]).longValue();
            }
        }
        List<Long> lista = new ArrayList<>(tamanho);
        for (long v : buckets) {
            lista.add(v);
        }
        return lista;
    }

    /** Classifica os user-agents dos últimos 30 dias por dispositivo/SO (para o item G do roadmap). */
    private List<UsoResponse.Contagem> dispositivos(LocalDateTime desde) {
        Map<String, Long> mapa = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                        "select user_agent, count(*) from acesso_evento where data_hora >= :desde group by user_agent")
                .setParameter("desde", desde).getResultList();
        for (Object[] l : linhas) {
            String rotulo = classificar((String) l[0]);
            mapa.merge(rotulo, ((Number) l[1]).longValue(), Long::sum);
        }
        List<UsoResponse.Contagem> lista = new ArrayList<>();
        mapa.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> lista.add(new UsoResponse.Contagem(e.getKey(), e.getValue())));
        return lista;
    }

    private static String classificar(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Desconhecido";
        }
        String s = ua.toLowerCase();
        if (s.contains("ipad")) {
            return "iPad";
        }
        if (s.contains("iphone") || s.contains("ios")) {
            return "iPhone (iOS)";
        }
        if (s.contains("android")) {
            return "Android";
        }
        if (s.contains("windows")) {
            return "Computador (Windows)";
        }
        if (s.contains("mac os") || s.contains("macintosh")) {
            return "Computador (Mac)";
        }
        if (s.contains("linux")) {
            return "Computador (Linux)";
        }
        return "Outro";
    }

    private String papel(Usuario u) {
        List<String> ps = new ArrayList<>();
        if (u.isEhAdmin()) { ps.add("Admin"); }
        if (u.isEhProfessor()) { ps.add("Professor"); }
        if (u.isEhAluno()) { ps.add("Aluno"); }
        if (u.isEhTesoureiro()) { ps.add("Tesoureiro"); }
        if (u.isEhLider()) { ps.add("Líder"); }
        return ps.isEmpty() ? "—" : String.join("/", ps);
    }

    private long scalar(String jpql) {
        return ((Number) em.createQuery(jpql).getSingleResult()).longValue();
    }

    private long scalarParam(String jpql, LocalDateTime desde) {
        return ((Number) em.createQuery(jpql).setParameter("desde", desde).getSingleResult()).longValue();
    }

    private static double pct(long parte, long total) {
        return total > 0 ? Math.round(parte * 10000.0 / total) / 100.0 : 0.0;
    }
}
