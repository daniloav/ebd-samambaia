package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.QuizDto;
import br.com.ice.ebd.model.Alternativa;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.model.Questao;
import br.com.ice.ebd.model.TipoProva;
import br.com.ice.ebd.model.TipoQuestao;
import br.com.ice.ebd.repository.AlternativaRepository;
import br.com.ice.ebd.repository.ProvaRepository;
import br.com.ice.ebd.repository.QuestaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Montagem/edição das questões de uma prova ONLINE (quiz). Correção fica no aluno (Etapa 2). */
@ApplicationScoped
public class QuizService {

    @Inject EscopoService escopo;
    @Inject ProvaRepository provaRepository;
    @Inject QuestaoRepository questaoRepository;
    @Inject AlternativaRepository alternativaRepository;

    /** Questões com o gabarito (visão do professor). */
    public List<QuizDto.QuestaoEdit> obterQuestoes(Long provaId) {
        Prova p = obter(provaId);
        escopo.assertClasse(p.getClasse().getId());
        List<QuizDto.QuestaoEdit> out = new ArrayList<>();
        for (Questao q : questaoRepository.listarPorProva(provaId)) {
            List<QuizDto.AlternativaEdit> alts = alternativaRepository.listarPorQuestao(q.getId()).stream()
                    .map(a -> new QuizDto.AlternativaEdit(a.getId(), a.getTexto(), a.isCorreta()))
                    .toList();
            out.add(new QuizDto.QuestaoEdit(q.getId(), q.getEnunciado(), q.getTipo().name(), q.getPontos(), alts));
        }
        return out;
    }

    /** Substitui todas as questões da prova e a marca como ONLINE (nota máxima = soma dos pontos). */
    @Transactional
    public void salvarQuestoes(Long provaId, QuizDto.Salvar req) {
        Prova p = obter(provaId);
        escopo.assertClasse(p.getClasse().getId());
        validar(req);

        questaoRepository.apagarPorProva(provaId); // alternativas/respostas caem por cascade no banco
        questaoRepository.flush();

        BigDecimal total = BigDecimal.ZERO;
        int ordemQ = 0;
        for (QuizDto.QuestaoIn qi : req.questoes()) {
            Questao q = new Questao();
            q.setProva(p);
            q.setEnunciado(qi.enunciado().trim());
            q.setTipo(TipoQuestao.valueOf(qi.tipo()));
            BigDecimal pontos = (qi.pontos() != null && qi.pontos().signum() > 0) ? qi.pontos() : BigDecimal.ONE;
            q.setPontos(pontos);
            q.setOrdem(ordemQ++);
            questaoRepository.persist(q);
            total = total.add(pontos);

            int ordemA = 0;
            for (QuizDto.AlternativaIn ai : qi.alternativas()) {
                Alternativa a = new Alternativa();
                a.setQuestao(q);
                a.setTexto(ai.texto().trim());
                a.setCorreta(ai.correta());
                a.setOrdem(ordemA++);
                alternativaRepository.persist(a);
            }
        }
        p.setTipo(TipoProva.ONLINE);
        p.setNotaMaxima(total);
    }

    private void validar(QuizDto.Salvar req) {
        if (req == null || req.questoes() == null || req.questoes().isEmpty()) {
            throw bad("Adicione ao menos uma questão.");
        }
        int n = 1;
        for (QuizDto.QuestaoIn q : req.questoes()) {
            if (q.enunciado() == null || q.enunciado().isBlank()) {
                throw bad("A questão " + n + " está sem enunciado.");
            }
            TipoQuestao tipo;
            try {
                tipo = TipoQuestao.valueOf(q.tipo());
            } catch (Exception e) {
                throw bad("Tipo inválido na questão " + n + " (use MULTIPLA ou VF).");
            }
            List<QuizDto.AlternativaIn> alts = q.alternativas();
            if (alts == null || alts.isEmpty()) {
                throw bad("A questão " + n + " está sem alternativas.");
            }
            if (tipo == TipoQuestao.VF && alts.size() != 2) {
                throw bad("A questão " + n + " (V/F) deve ter exatamente 2 alternativas.");
            }
            if (tipo == TipoQuestao.MULTIPLA && alts.size() < 2) {
                throw bad("A questão " + n + " precisa de pelo menos 2 alternativas.");
            }
            long corretas = alts.stream().filter(QuizDto.AlternativaIn::correta).count();
            if (corretas != 1) {
                throw bad("A questão " + n + " deve ter exatamente 1 alternativa correta.");
            }
            for (QuizDto.AlternativaIn a : alts) {
                if (a.texto() == null || a.texto().isBlank()) {
                    throw bad("Há alternativa sem texto na questão " + n + ".");
                }
            }
            n++;
        }
    }

    private Prova obter(Long id) {
        Prova p = provaRepository.findById(id);
        if (p == null) {
            throw new NotFoundException("Prova não encontrada: " + id);
        }
        return p;
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(msg, Response.Status.BAD_REQUEST);
    }
}
