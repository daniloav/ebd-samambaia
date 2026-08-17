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
    private static final int EBD_HORA_INI = 8;   // janela do culto/EBD de domingo (BRT)
    private static final int EBD_HORA_FIM = 12;
    private static final int SEMANAS_STREAK = 52; // janela p/ calcular streak de semanas
    private static final java.time.ZoneId FUSO = java.time.ZoneId.of("America/Sao_Paulo");

    // Fluxo de atividade = logins (acesso_evento) + page views/cliques (uso_evento). Constantes literais.
    private static final String SQL_PICO =
            "select coalesce(max(c), 0) from ("
            + " select floor(extract(epoch from data_hora) / 900) b, count(distinct usuario_id) c from ("
            + "   select usuario_id, data_hora from acesso_evento where data_hora >= :desde"
            + "   union all select usuario_id, data_hora from uso_evento where data_hora >= :desde"
            + " ) t group by b) x";
    private static final String SQL_AO_VIVO =
            "select count(distinct usuario_id) from ("
            + " select usuario_id, data_hora from acesso_evento where data_hora between :ini and :fim"
            + " union all select usuario_id, data_hora from uso_evento where data_hora between :ini and :fim"
            + ") t";
    private static final String SQL_FORA_DOMINGO =
            "select coalesce(sum(case when extract(dow from data_hora) <> 0 then 1 else 0 end), 0), count(*) from ("
            + " select data_hora from acesso_evento where data_hora >= :desde"
            + " union all select data_hora from uso_evento where data_hora >= :desde) t";
    private static final String SQL_COORTES =
            "select to_char(data_cadastro, 'YYYY-MM') m, count(*),"
            + " count(*) filter (where ultimo_acesso is not null),"
            + " count(*) filter (where ultimo_acesso >= :ha30)"
            + " from usuario where ativo = true group by m order by m desc limit 6";
    private static final String SQL_STREAK_SEMANAS =
            "select u.id, u.username, cast(date_trunc('week', t.data_hora) as date) wk from usuario u join ("
            + " select usuario_id, data_hora from acesso_evento where data_hora >= :desde"
            + " union all select usuario_id, data_hora from uso_evento where data_hora >= :desde"
            + ") t on t.usuario_id = u.id where u.eh_aluno = true and u.ativo = true"
            + " group by u.id, u.username, wk";

    /** Última aula prevista (não adiada, até hoje) de cada turma ativa + se ela já tem chamada. */
    private static final String SQL_COBERTURA_TURMAS =
            "select c.nome, ult.data, exists (select 1 from presenca p where p.aula_id = ult.id) "
            + "from classe c left join lateral ("
            + "  select a.id, a.data from aula a"
            + "  where a.classe_id = c.id and a.adiada = false and a.data <= :hoje"
            + "  order by a.data desc limit 1"
            + ") ult on true where c.ativo = true order by c.nome";

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
                professoresMaisAtivos(ha30), chamadaPrazo(), coberturaTurmas(),
                // A) Tempo real (lote 3)
                pico(inicioHoje), pico(ha30),
                aoVivoNaAula(), ultimoDomingo(),
                // C) Funil + coorte (lote 3)
                funil(ha30), coortes(ha30),
                // E) Engajamento do aluno (lote 3)
                streaks(agora.minusWeeks(SEMANAS_STREAK)), pctForaDoDomingo(ha30),
                // G) Técnico (lote 3)
                classificarUA(ha30, UsoService::plataforma), classificarUA(ha30, UsoService::versaoSO));
    }

    // ============================ A) Tempo real (pico + ao vivo) ============================

    /** Pico de atividade simultânea: máx. de usuários distintos numa mesma janela de 15 min. */
    private long pico(java.time.LocalDateTime desde) {
        Number n = (Number) em.createNativeQuery(SQL_PICO).setParameter("desde", desde).getSingleResult();
        return n.longValue();
    }

    private LocalDate ultimoDomingo() {
        return LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
    }

    /** Usuários ativos na janela da EBD (08–12h) do último domingo. */
    private long aoVivoNaAula() {
        LocalDate dom = ultimoDomingo();
        Number n = (Number) em.createNativeQuery(SQL_AO_VIVO)
                .setParameter("ini", dom.atTime(EBD_HORA_INI, 0))
                .setParameter("fim", dom.atTime(EBD_HORA_FIM, 0))
                .getSingleResult();
        return n.longValue();
    }

    // ============================ C) Funil + coorte ============================

    /** Funil de ativação: cadastrado → 1º acesso → trocou a senha padrão → usou uma função. */
    private List<UsoResponse.EtapaFunil> funil(java.time.LocalDateTime ha30) {
        long cadastrados = scalar("select count(u) from Usuario u where u.ativo = true");
        long acessaram = scalar("select count(u) from Usuario u where u.ativo = true and u.ultimoAcesso is not null");
        long trocaram = scalar("select count(u) from Usuario u where u.ativo = true and u.ultimoAcesso is not null "
                + "and u.precisaTrocarSenha = false");
        long usaram = scalar("select count(distinct e.usuario.id) from UsoEvento e where e.usuario.ativo = true");
        List<UsoResponse.EtapaFunil> f = new ArrayList<>();
        f.add(new UsoResponse.EtapaFunil("Cadastrados", cadastrados));
        f.add(new UsoResponse.EtapaFunil("1º acesso", acessaram));
        f.add(new UsoResponse.EtapaFunil("Trocaram a senha", trocaram));
        f.add(new UsoResponse.EtapaFunil("Usaram uma função", usaram));
        return f;
    }

    /** Coortes por mês de cadastro (últimos 6): cadastrados, quantos ativaram e quantos seguem ativos (30d). */
    private List<UsoResponse.Coorte> coortes(java.time.LocalDateTime ha30) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(SQL_COORTES).setParameter("ha30", ha30).getResultList();
        List<UsoResponse.Coorte> lista = new ArrayList<>();
        for (Object[] l : linhas) {
            lista.add(new UsoResponse.Coorte(formatarMes((String) l[0]),
                    ((Number) l[1]).longValue(), ((Number) l[2]).longValue(), ((Number) l[3]).longValue()));
        }
        java.util.Collections.reverse(lista); // do mais antigo ao mais novo
        return lista;
    }

    private static String formatarMes(String yyyyMm) {
        if (yyyyMm == null || yyyyMm.length() != 7) {
            return String.valueOf(yyyyMm);
        }
        return yyyyMm.substring(5) + "/" + yyyyMm.substring(0, 4);
    }

    // ============================ E) Streak + fora do domingo ============================

    /** Semanas seguidas com atividade (contadas a partir desta semana, para trás) — top alunos. */
    private List<UsoResponse.StreakUsuario> streaks(java.time.LocalDateTime desde) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(SQL_STREAK_SEMANAS).setParameter("desde", desde).getResultList();
        Map<Long, String> nome = new java.util.HashMap<>();
        Map<Long, java.util.Set<LocalDate>> semanas = new java.util.HashMap<>();
        for (Object[] l : linhas) {
            Long uid = ((Number) l[0]).longValue();
            nome.put(uid, (String) l[1]);
            LocalDate seg = ((java.sql.Date) l[2]).toLocalDate();
            semanas.computeIfAbsent(uid, k -> new java.util.HashSet<>()).add(seg);
        }
        LocalDate estaSemana = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        List<UsoResponse.StreakUsuario> lista = new ArrayList<>();
        for (Map.Entry<Long, java.util.Set<LocalDate>> e : semanas.entrySet()) {
            int streak = 0;
            LocalDate cur = estaSemana;
            while (e.getValue().contains(cur)) {
                streak++;
                cur = cur.minusWeeks(1);
            }
            if (streak >= 1) {
                Usuario u = usuarioRepository.findByUsername(nome.get(e.getKey())).orElse(null);
                lista.add(new UsoResponse.StreakUsuario(nome.get(e.getKey()), u != null ? papel(u) : "Aluno", streak));
            }
        }
        lista.sort((a, b) -> Integer.compare(b.semanas(), a.semanas()));
        return lista.size() > TOP_LIMITE ? lista.subList(0, TOP_LIMITE) : lista;
    }

    /** % da atividade dos últimos 30 dias que aconteceu fora do domingo. */
    private double pctForaDoDomingo(java.time.LocalDateTime desde) {
        Object[] r = (Object[]) em.createNativeQuery(SQL_FORA_DOMINGO).setParameter("desde", desde).getSingleResult();
        long fora = ((Number) r[0]).longValue();
        long total = ((Number) r[1]).longValue();
        return pct(fora, total);
    }

    // ============================ G) Técnico (versão exata + plataforma) ============================

    /** Agrega os user-agents (30d) por um classificador (plataforma ou versão de SO). */
    private List<UsoResponse.Contagem> classificarUA(java.time.LocalDateTime desde,
            java.util.function.Function<String, String> classificador) {
        Map<String, Long> mapa = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                        "select user_agent, count(*) from acesso_evento where data_hora >= :desde group by user_agent")
                .setParameter("desde", desde).getResultList();
        for (Object[] l : linhas) {
            mapa.merge(classificador.apply((String) l[0]), ((Number) l[1]).longValue(), Long::sum);
        }
        List<UsoResponse.Contagem> lista = new ArrayList<>();
        mapa.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> lista.add(new UsoResponse.Contagem(e.getKey(), e.getValue())));
        return lista;
    }

    private static String plataforma(String ua) {
        if (ua == null || ua.isBlank()) { return "Desconhecido"; }
        String s = ua.toLowerCase();
        if (s.contains("iphone") || s.contains("ipad") || s.contains("android") || s.contains("mobile")) {
            return "Celular / tablet";
        }
        if (s.contains("windows") || s.contains("macintosh") || s.contains("mac os") || s.contains("linux")) {
            return "Computador";
        }
        return "Outro";
    }

    private static final java.util.regex.Pattern RE_IOS = java.util.regex.Pattern.compile("os (\\d+)[_.]");
    private static final java.util.regex.Pattern RE_ANDROID = java.util.regex.Pattern.compile("android (\\d+)");

    private static String versaoSO(String ua) {
        if (ua == null || ua.isBlank()) { return "Desconhecido"; }
        String s = ua.toLowerCase();
        if (s.contains("iphone") || s.contains("ipad") || (s.contains("mac os") && s.contains("mobile"))) {
            java.util.regex.Matcher m = RE_IOS.matcher(s);
            return m.find() ? "iOS " + m.group(1) : "iOS (outro)";
        }
        if (s.contains("android")) {
            java.util.regex.Matcher m = RE_ANDROID.matcher(s);
            return m.find() ? "Android " + m.group(1) : "Android (outro)";
        }
        if (s.contains("windows")) { return "Windows"; }
        if (s.contains("mac os") || s.contains("macintosh")) { return "macOS"; }
        if (s.contains("linux")) { return "Linux"; }
        return "Outro";
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
     * Cobertura da chamada por turma: para cada turma ativa, olha a <b>última aula prevista</b>
     * (não adiada, com data até hoje no fuso BRT) e diz se a chamada dela já foi registrada.
     * Turma sem aula elegível fica em SEM_AULA — não conta como pendência do professor.
     */
    private List<UsoResponse.CoberturaTurma> coberturaTurmas() {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(SQL_COBERTURA_TURMAS)
                .setParameter("hoje", LocalDate.now(FUSO)).getResultList();
        List<UsoResponse.CoberturaTurma> lista = new ArrayList<>();
        for (Object[] l : linhas) {
            LocalDate data = l[1] == null ? null : ((java.sql.Date) l[1]).toLocalDate();
            UsoResponse.SituacaoCobertura situacao;
            if (data == null) {
                situacao = UsoResponse.SituacaoCobertura.SEM_AULA;
            } else {
                situacao = Boolean.TRUE.equals(l[2])
                        ? UsoResponse.SituacaoCobertura.FEITA
                        : UsoResponse.SituacaoCobertura.PENDENTE;
            }
            lista.add(new UsoResponse.CoberturaTurma((String) l[0], situacao, data));
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
