package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.QuizAlunoDto;
import br.com.ice.ebd.model.Alternativa;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.NotaProva;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.model.Questao;
import br.com.ice.ebd.model.Resposta;
import br.com.ice.ebd.model.Submissao;
import br.com.ice.ebd.model.TipoProva;
import br.com.ice.ebd.repository.AlternativaRepository;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.NotaProvaRepository;
import br.com.ice.ebd.repository.ProvaRepository;
import br.com.ice.ebd.repository.QuestaoRepository;
import br.com.ice.ebd.repository.RespostaRepository;
import br.com.ice.ebd.repository.SubmissaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visão do aluno para provas respondidas pela tela: lista as provas da sua turma, entrega o quiz
 * para responder (sem gabarito) e recebe a submissão, corrigindo automaticamente.
 * <ul>
 *   <li><b>ONLINE</b>: 1 tentativa; a nota vai para {@link NotaProva} (boletim/rankings/e-mail).</li>
 *   <li><b>RECUPERACAO</b>: até {@link Recuperacao#TENTATIVAS} tentativas, questões e alternativas
 *   embaralhadas a cada uma, gabarito após cada envio; vale a melhor nota, que vira pontos de
 *   presença na aula (calculados no ranking) — por isso <b>não</b> grava {@link NotaProva}.</li>
 * </ul>
 * O aluno nunca informa o próprio id — ele vem do vínculo do usuário logado.
 */
@ApplicationScoped
public class QuizAlunoService {

    @Inject EscopoService escopo;
    @Inject AlunoRepository alunoRepository;
    @Inject ProvaRepository provaRepository;
    @Inject QuestaoRepository questaoRepository;
    @Inject AlternativaRepository alternativaRepository;
    @Inject SubmissaoRepository submissaoRepository;
    @Inject RespostaRepository respostaRepository;
    @Inject NotaProvaRepository notaRepository;
    @Inject NotificacaoService notificacaoService;

    /** Provas respondíveis da turma do aluno, com status, tentativas e a melhor nota. */
    @Transactional
    public List<QuizAlunoDto.ProvaResumo> listarMinhas() {
        Aluno aluno = alunoLogado();
        Long classeId = aluno.getClasse().getId();
        LocalDateTime agora = LocalDateTime.now();
        List<QuizAlunoDto.ProvaResumo> out = new ArrayList<>();
        for (Prova p : provaRepository.listarPorClasse(classeId)) {
            if (!p.getTipo().isQuiz()) {
                continue;
            }
            List<Submissao> tentativas = submissaoRepository.tentativasDoAluno(p.getId(), aluno.getId());
            BigDecimal melhor = melhorNota(tentativas);
            QuizAlunoDto.Status status = status(p, tentativas.size(), melhor, agora);
            long numQuestoes = questaoRepository.count("prova.id", p.getId());
            out.add(new QuizAlunoDto.ProvaResumo(
                    p.getId(), p.getTitulo(), dataDaProva(p), p.getNotaMaxima(), numQuestoes,
                    status.name(), p.getAbreEm(), p.getFechaEm(), melhor, p.getTipo().name(),
                    tentativas.size(), tentativasMax(p), aulaData(p), aulaTema(p),
                    presencaEquivalente(p, melhor)));
        }
        return out;
    }

    /**
     * O quiz para responder (sem gabarito). Exige janela aberta e tentativa disponível. Na
     * recuperação, questões e alternativas vêm numa ordem nova a cada chamada.
     */
    @Transactional
    public QuizAlunoDto.ParaResponder obterParaResponder(Long provaId) {
        Aluno aluno = alunoLogado();
        Prova p = provaDoAluno(provaId, aluno);
        List<Submissao> tentativas = submissaoRepository.tentativasDoAluno(provaId, aluno.getId());
        exigirTentativaDisponivel(p, tentativas);
        exigirJanelaAberta(p);

        boolean embaralhar = p.getTipo() == TipoProva.RECUPERACAO;
        List<QuizAlunoDto.QuestaoResponder> questoes = new ArrayList<>();
        for (Questao q : questaoRepository.listarPorProva(provaId)) {
            List<QuizAlunoDto.AlternativaResponder> alts = new ArrayList<>(
                    alternativaRepository.listarPorQuestao(q.getId()).stream()
                            .map(a -> new QuizAlunoDto.AlternativaResponder(a.getId(), a.getTexto()))
                            .toList());
            if (embaralhar) {
                Collections.shuffle(alts);
            }
            questoes.add(new QuizAlunoDto.QuestaoResponder(
                    q.getId(), q.getEnunciado(), q.getTipo().name(), q.getPontos(), alts));
        }
        if (questoes.isEmpty()) {
            throw bad("Esta prova ainda não tem questões.");
        }
        if (embaralhar) {
            Collections.shuffle(questoes);
        }
        return new QuizAlunoDto.ParaResponder(p.getId(), p.getTitulo(), p.getNotaMaxima(), questoes,
                p.getTipo().name(), tentativas.size() + 1, tentativasMax(p), aulaData(p), aulaTema(p));
    }

    /** Recebe as respostas, corrige automaticamente e grava a tentativa (e a nota, na ONLINE). */
    @Transactional
    public QuizAlunoDto.Resultado submeter(Long provaId, QuizAlunoDto.SubmeterRequest req) {
        Aluno aluno = alunoLogado();
        Prova p = provaDoAluno(provaId, aluno);
        List<Submissao> tentativas = submissaoRepository.tentativasDoAluno(provaId, aluno.getId());
        exigirTentativaDisponivel(p, tentativas);
        exigirJanelaAberta(p);

        // Escolha por questão + a ordem em que o aluno as viu (embaralhada na recuperação),
        // para devolver a correção na mesma ordem da tela.
        Map<Long, Long> escolhas = new HashMap<>();
        Map<Long, Integer> ordemNaTela = new HashMap<>();
        if (req != null && req.respostas() != null) {
            for (QuizAlunoDto.RespostaIn r : req.respostas()) {
                if (r.questaoId() != null) {
                    escolhas.put(r.questaoId(), r.alternativaId());
                    ordemNaTela.putIfAbsent(r.questaoId(), ordemNaTela.size());
                }
            }
        }

        List<Questao> questoes = new ArrayList<>(questaoRepository.listarPorProva(provaId));
        if (questoes.isEmpty()) {
            throw bad("Esta prova não tem questões.");
        }
        questoes.sort(Comparator.comparingInt(q -> ordemNaTela.getOrDefault(q.getId(), Integer.MAX_VALUE)));

        int numeroTentativa = tentativas.size() + 1;
        Submissao sub = new Submissao();
        sub.setProva(p);
        sub.setAluno(aluno);
        sub.setTentativa(numeroTentativa);
        sub.setEnviadaEm(LocalDateTime.now());
        sub.setNota(BigDecimal.ZERO); // recalculada abaixo
        submissaoRepository.persist(sub);

        BigDecimal nota = BigDecimal.ZERO;
        int acertos = 0;
        List<QuizAlunoDto.ResultadoQuestao> detalhe = new ArrayList<>();
        for (Questao q : questoes) {
            List<Alternativa> alts = alternativaRepository.listarPorQuestao(q.getId());
            Long escolhidaId = escolhas.get(q.getId());
            boolean pertence = escolhidaId != null && alts.stream().anyMatch(a -> a.getId().equals(escolhidaId));
            Long escolhidaValida = pertence ? escolhidaId : null;
            QuizAlunoDto.ResultadoQuestao rq = corrigir(q, alts, escolhidaValida);
            if (rq.acertou()) {
                nota = nota.add(q.getPontos());
                acertos++;
            }

            Resposta resp = new Resposta();
            resp.setSubmissao(sub);
            resp.setQuestao(q);
            resp.setAlternativa(escolhidaValida != null ? alternativaRepository.findById(escolhidaValida) : null);
            respostaRepository.persist(resp);
            detalhe.add(rq);
        }
        sub.setNota(nota);

        if (p.getTipo() == TipoProva.ONLINE) {
            // Upsert em NotaProva -> alimenta boletim, rankings e o e-mail de desempenho.
            NotaProva np = notaRepository.find("prova.id = ?1 and aluno.id = ?2", provaId, aluno.getId()).firstResult();
            if (np == null) {
                np = new NotaProva();
                np.setProva(p);
                np.setAluno(aluno);
            }
            np.setNota(nota);
            notaRepository.persist(np);

            // E-mail de desempenho (respeita o opt-in do aluno), como no "lançar e notificar".
            if (aluno.getEmail() != null && !aluno.getEmail().isBlank() && aluno.isRecebeNotificacoes()
                    && notificacaoService.enviarNotaProva(aluno, p, nota)) {
                np.setNotificadaNota(nota); // dedup: não reenvia esta nota depois
            }
        }

        BigDecimal melhor = melhorNota(tentativas);
        melhor = melhor == null || nota.compareTo(melhor) > 0 ? nota : melhor;
        return new QuizAlunoDto.Resultado(p.getTitulo(), nota, p.getNotaMaxima(), acertos, questoes.size(), detalhe,
                p.getTipo().name(), numeroTentativa, tentativasMax(p), melhor, presencaEquivalente(p, melhor),
                podeTentarDeNovo(p, numeroTentativa, melhor));
    }

    /** Resultado da última tentativa do aluno (nota + gabarito), com a melhor nota entre todas. */
    @Transactional
    public QuizAlunoDto.Resultado obterResultado(Long provaId) {
        Aluno aluno = alunoLogado();
        Prova p = provaDoAluno(provaId, aluno);
        List<Submissao> tentativas = submissaoRepository.tentativasDoAluno(provaId, aluno.getId());
        if (tentativas.isEmpty()) {
            throw new NotFoundException("Você ainda não respondeu esta prova.");
        }
        Submissao sub = tentativas.get(tentativas.size() - 1);
        Map<Long, Long> escolhidas = new HashMap<>();
        for (Resposta r : respostaRepository.list("submissao.id", sub.getId())) {
            escolhidas.put(r.getQuestao().getId(), r.getAlternativa() != null ? r.getAlternativa().getId() : null);
        }
        List<QuizAlunoDto.ResultadoQuestao> detalhe = new ArrayList<>();
        int acertos = 0;
        List<Questao> questoes = questaoRepository.listarPorProva(provaId);
        for (Questao q : questoes) {
            QuizAlunoDto.ResultadoQuestao rq = corrigir(q, alternativaRepository.listarPorQuestao(q.getId()),
                    escolhidas.get(q.getId()));
            if (rq.acertou()) {
                acertos++;
            }
            detalhe.add(rq);
        }
        BigDecimal melhor = melhorNota(tentativas);
        return new QuizAlunoDto.Resultado(p.getTitulo(), sub.getNota(), p.getNotaMaxima(), acertos, questoes.size(), detalhe,
                p.getTipo().name(), sub.getTentativa(), tentativasMax(p), melhor, presencaEquivalente(p, melhor),
                podeTentarDeNovo(p, tentativas.size(), melhor) && janelaAberta(p));
    }

    // ---------- helpers ----------

    /** Correção de uma questão, com o gabarito (a alternativa correta). */
    private QuizAlunoDto.ResultadoQuestao corrigir(Questao q, List<Alternativa> alts, Long escolhidaId) {
        Long corretaId = alts.stream().filter(Alternativa::isCorreta).map(Alternativa::getId).findFirst().orElse(null);
        boolean acertou = escolhidaId != null && escolhidaId.equals(corretaId);
        List<QuizAlunoDto.AlternativaResponder> altsDto = alts.stream()
                .map(a -> new QuizAlunoDto.AlternativaResponder(a.getId(), a.getTexto())).toList();
        return new QuizAlunoDto.ResultadoQuestao(
                q.getId(), q.getEnunciado(), escolhidaId, corretaId, acertou, q.getPontos(), altsDto);
    }

    private static int tentativasMax(Prova p) {
        return p.getTipo() == TipoProva.RECUPERACAO ? Recuperacao.TENTATIVAS : 1;
    }

    private static BigDecimal melhorNota(List<Submissao> tentativas) {
        return tentativas.stream().map(Submissao::getNota).max(Comparator.naturalOrder()).orElse(null);
    }

    /** Ainda há tentativa e ainda vale a pena: quem já tirou a nota máxima não refaz. */
    private static boolean podeTentarDeNovo(Prova p, int usadas, BigDecimal melhor) {
        return usadas < tentativasMax(p) && (melhor == null || melhor.compareTo(p.getNotaMaxima()) < 0);
    }

    private static BigDecimal presencaEquivalente(Prova p, BigDecimal melhor) {
        return p.getTipo() == TipoProva.RECUPERACAO && melhor != null
                ? Recuperacao.fracaoPresenca(melhor, p.getNotaMaxima()) : null;
    }

    /** Na recuperação a data que importa é a da aula (a agenda pode ter sido remanejada). */
    private static LocalDate dataDaProva(Prova p) {
        return p.getAula() != null ? p.getAula().getData() : p.getData();
    }

    private static LocalDate aulaData(Prova p) {
        return p.getAula() != null ? p.getAula().getData() : null;
    }

    private static String aulaTema(Prova p) {
        return p.getAula() != null ? p.getAula().getTema() : null;
    }

    private QuizAlunoDto.Status status(Prova p, int usadas, BigDecimal melhor, LocalDateTime agora) {
        if (usadas > 0 && !podeTentarDeNovo(p, usadas, melhor)) {
            return QuizAlunoDto.Status.RESPONDIDA;
        }
        if (p.getAbreEm() != null && agora.isBefore(p.getAbreEm())) {
            return QuizAlunoDto.Status.FUTURA;
        }
        if (p.getFechaEm() != null && agora.isAfter(p.getFechaEm())) {
            // Prazo encerrado com tentativas feitas: o que conta é o resultado.
            return usadas > 0 ? QuizAlunoDto.Status.RESPONDIDA : QuizAlunoDto.Status.FECHADA;
        }
        return QuizAlunoDto.Status.DISPONIVEL;
    }

    private void exigirTentativaDisponivel(Prova p, List<Submissao> tentativas) {
        if (tentativas.isEmpty()) {
            return;
        }
        BigDecimal melhor = melhorNota(tentativas);
        if (podeTentarDeNovo(p, tentativas.size(), melhor)) {
            return;
        }
        if (p.getTipo() != TipoProva.RECUPERACAO) {
            throw bad("Você já respondeu esta prova.");
        }
        if (melhor.compareTo(p.getNotaMaxima()) >= 0) {
            throw bad("Você já tirou a nota máxima nesta recuperação.");
        }
        throw bad("Você já usou as " + Recuperacao.TENTATIVAS + " tentativas desta recuperação.");
    }

    private static boolean janelaAberta(Prova p) {
        LocalDateTime agora = LocalDateTime.now();
        return !(p.getAbreEm() != null && agora.isBefore(p.getAbreEm()))
                && !(p.getFechaEm() != null && agora.isAfter(p.getFechaEm()));
    }

    private void exigirJanelaAberta(Prova p) {
        LocalDateTime agora = LocalDateTime.now();
        if (p.getAbreEm() != null && agora.isBefore(p.getAbreEm())) {
            throw new WebApplicationException("Esta prova ainda não está disponível.", Response.Status.FORBIDDEN);
        }
        if (p.getFechaEm() != null && agora.isAfter(p.getFechaEm())) {
            throw new WebApplicationException("O prazo desta prova já encerrou.", Response.Status.FORBIDDEN);
        }
    }

    private Aluno alunoLogado() {
        Long alunoId = escopo.alunoIdLogado();
        if (alunoId == null) {
            throw new ForbiddenException("Seu usuário não está vinculado a um aluno.");
        }
        Aluno aluno = alunoRepository.findById(alunoId);
        if (aluno == null || aluno.getClasse() == null) {
            throw new NotFoundException("Aluno não encontrado.");
        }
        return aluno;
    }

    /** Carrega a prova garantindo que é respondível pela tela e da turma do aluno. */
    private Prova provaDoAluno(Long provaId, Aluno aluno) {
        Prova p = provaRepository.findById(provaId);
        if (p == null || !p.getTipo().isQuiz()) {
            throw new NotFoundException("Prova online não encontrada: " + provaId);
        }
        if (!p.getClasse().getId().equals(aluno.getClasse().getId())) {
            throw new ForbiddenException("Esta prova não é da sua turma.");
        }
        return p;
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(msg, Response.Status.BAD_REQUEST);
    }
}
