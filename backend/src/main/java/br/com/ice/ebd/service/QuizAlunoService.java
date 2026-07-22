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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visão do aluno para provas ONLINE: lista as provas da sua turma, entrega o quiz
 * para responder (sem gabarito) e recebe a submissão, corrigindo automaticamente e
 * gravando a nota em {@link NotaProva} (que alimenta boletim/rankings/e-mail de nota).
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

    /** Provas ONLINE da turma do aluno, com status e a nota (se já respondeu). */
    @Transactional
    public List<QuizAlunoDto.ProvaResumo> listarMinhas() {
        Aluno aluno = alunoLogado();
        Long classeId = aluno.getClasse().getId();
        LocalDateTime agora = LocalDateTime.now();
        List<QuizAlunoDto.ProvaResumo> out = new ArrayList<>();
        for (Prova p : provaRepository.listarPorClasse(classeId)) {
            if (p.getTipo() != TipoProva.ONLINE) {
                continue;
            }
            Submissao s = submissaoRepository.doAlunoNaProva(p.getId(), aluno.getId());
            QuizAlunoDto.Status status = status(p, s, agora);
            long numQuestoes = questaoRepository.count("prova.id", p.getId());
            out.add(new QuizAlunoDto.ProvaResumo(
                    p.getId(), p.getTitulo(), p.getData(), p.getNotaMaxima(), numQuestoes,
                    status.name(), p.getAbreEm(), p.getFechaEm(), s != null ? s.getNota() : null));
        }
        return out;
    }

    /** O quiz para responder (sem gabarito). Exige janela aberta e prova ainda não respondida. */
    @Transactional
    public QuizAlunoDto.ParaResponder obterParaResponder(Long provaId) {
        Aluno aluno = alunoLogado();
        Prova p = provaDoAluno(provaId, aluno);
        if (submissaoRepository.doAlunoNaProva(provaId, aluno.getId()) != null) {
            throw bad("Você já respondeu esta prova.");
        }
        exigirJanelaAberta(p);

        List<QuizAlunoDto.QuestaoResponder> questoes = new ArrayList<>();
        for (Questao q : questaoRepository.listarPorProva(provaId)) {
            List<QuizAlunoDto.AlternativaResponder> alts = alternativaRepository.listarPorQuestao(q.getId()).stream()
                    .map(a -> new QuizAlunoDto.AlternativaResponder(a.getId(), a.getTexto()))
                    .toList();
            questoes.add(new QuizAlunoDto.QuestaoResponder(
                    q.getId(), q.getEnunciado(), q.getTipo().name(), q.getPontos(), alts));
        }
        if (questoes.isEmpty()) {
            throw bad("Esta prova ainda não tem questões.");
        }
        return new QuizAlunoDto.ParaResponder(p.getId(), p.getTitulo(), p.getNotaMaxima(), questoes);
    }

    /** Recebe as respostas, corrige automaticamente, grava a submissão e a nota. */
    @Transactional
    public QuizAlunoDto.Resultado submeter(Long provaId, QuizAlunoDto.SubmeterRequest req) {
        Aluno aluno = alunoLogado();
        Prova p = provaDoAluno(provaId, aluno);
        if (submissaoRepository.doAlunoNaProva(provaId, aluno.getId()) != null) {
            throw bad("Você já respondeu esta prova.");
        }
        exigirJanelaAberta(p);

        Map<Long, Long> escolhas = new HashMap<>();
        if (req != null && req.respostas() != null) {
            for (QuizAlunoDto.RespostaIn r : req.respostas()) {
                if (r.questaoId() != null) {
                    escolhas.put(r.questaoId(), r.alternativaId());
                }
            }
        }

        List<Questao> questoes = questaoRepository.listarPorProva(provaId);
        if (questoes.isEmpty()) {
            throw bad("Esta prova não tem questões.");
        }

        Submissao sub = new Submissao();
        sub.setProva(p);
        sub.setAluno(aluno);
        sub.setEnviadaEm(LocalDateTime.now());
        sub.setNota(BigDecimal.ZERO); // recalculada abaixo
        submissaoRepository.persist(sub);

        BigDecimal nota = BigDecimal.ZERO;
        int acertos = 0;
        List<QuizAlunoDto.ResultadoQuestao> detalhe = new ArrayList<>();
        for (Questao q : questoes) {
            List<Alternativa> alts = alternativaRepository.listarPorQuestao(q.getId());
            Long corretaId = alts.stream().filter(Alternativa::isCorreta).map(Alternativa::getId).findFirst().orElse(null);
            Long escolhidaId = escolhas.get(q.getId());
            boolean pertence = escolhidaId != null && alts.stream().anyMatch(a -> a.getId().equals(escolhidaId));
            Long escolhidaValida = pertence ? escolhidaId : null;
            boolean acertou = escolhidaValida != null && escolhidaValida.equals(corretaId);
            if (acertou) {
                nota = nota.add(q.getPontos());
                acertos++;
            }

            Resposta resp = new Resposta();
            resp.setSubmissao(sub);
            resp.setQuestao(q);
            resp.setAlternativa(escolhidaValida != null ? alternativaRepository.findById(escolhidaValida) : null);
            respostaRepository.persist(resp);

            List<QuizAlunoDto.AlternativaResponder> altsDto = alts.stream()
                    .map(a -> new QuizAlunoDto.AlternativaResponder(a.getId(), a.getTexto())).toList();
            detalhe.add(new QuizAlunoDto.ResultadoQuestao(
                    q.getId(), q.getEnunciado(), escolhidaValida, corretaId, acertou, q.getPontos(), altsDto));
        }
        sub.setNota(nota);

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
        if (aluno.getEmail() != null && !aluno.getEmail().isBlank() && aluno.isRecebeNotificacoes()) {
            notificacaoService.enviarNotaProva(aluno, p, nota);
        }

        return new QuizAlunoDto.Resultado(p.getTitulo(), nota, p.getNotaMaxima(), acertos, questoes.size(), detalhe);
    }

    /** Recupera o resultado de uma prova já respondida pelo aluno (nota + gabarito). */
    @Transactional
    public QuizAlunoDto.Resultado obterResultado(Long provaId) {
        Aluno aluno = alunoLogado();
        Prova p = provaDoAluno(provaId, aluno);
        Submissao sub = submissaoRepository.doAlunoNaProva(provaId, aluno.getId());
        if (sub == null) {
            throw new NotFoundException("Você ainda não respondeu esta prova.");
        }
        Map<Long, Long> escolhidas = new HashMap<>();
        for (Resposta r : respostaRepository.list("submissao.id", sub.getId())) {
            escolhidas.put(r.getQuestao().getId(), r.getAlternativa() != null ? r.getAlternativa().getId() : null);
        }
        List<QuizAlunoDto.ResultadoQuestao> detalhe = new ArrayList<>();
        int acertos = 0;
        List<Questao> questoes = questaoRepository.listarPorProva(provaId);
        for (Questao q : questoes) {
            List<Alternativa> alts = alternativaRepository.listarPorQuestao(q.getId());
            Long corretaId = alts.stream().filter(Alternativa::isCorreta).map(Alternativa::getId).findFirst().orElse(null);
            Long escolhidaId = escolhidas.get(q.getId());
            boolean acertou = escolhidaId != null && escolhidaId.equals(corretaId);
            if (acertou) {
                acertos++;
            }
            List<QuizAlunoDto.AlternativaResponder> altsDto = alts.stream()
                    .map(a -> new QuizAlunoDto.AlternativaResponder(a.getId(), a.getTexto())).toList();
            detalhe.add(new QuizAlunoDto.ResultadoQuestao(
                    q.getId(), q.getEnunciado(), escolhidaId, corretaId, acertou, q.getPontos(), altsDto));
        }
        return new QuizAlunoDto.Resultado(p.getTitulo(), sub.getNota(), p.getNotaMaxima(), acertos, questoes.size(), detalhe);
    }

    // ---------- helpers ----------

    private QuizAlunoDto.Status status(Prova p, Submissao s, LocalDateTime agora) {
        if (s != null) {
            return QuizAlunoDto.Status.RESPONDIDA;
        }
        if (p.getAbreEm() != null && agora.isBefore(p.getAbreEm())) {
            return QuizAlunoDto.Status.FUTURA;
        }
        if (p.getFechaEm() != null && agora.isAfter(p.getFechaEm())) {
            return QuizAlunoDto.Status.FECHADA;
        }
        return QuizAlunoDto.Status.DISPONIVEL;
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

    /** Carrega a prova garantindo que é ONLINE e da turma do aluno. */
    private Prova provaDoAluno(Long provaId, Aluno aluno) {
        Prova p = provaRepository.findById(provaId);
        if (p == null || p.getTipo() != TipoProva.ONLINE) {
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
