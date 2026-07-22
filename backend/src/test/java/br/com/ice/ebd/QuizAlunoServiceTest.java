package br.com.ice.ebd;

import br.com.ice.ebd.dto.QuizAlunoDto;
import br.com.ice.ebd.dto.QuizDto;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.repository.NotaProvaRepository;
import br.com.ice.ebd.service.QuizAlunoService;
import br.com.ice.ebd.service.QuizService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class QuizAlunoServiceTest {

    @Inject QuizService quizService;          // montar o quiz (professor/admin)
    @Inject QuizAlunoService quizAlunoService; // responder (aluno)
    @Inject NotaProvaRepository notaRepository;
    @Inject Fixtures fx;

    /** Monta um quiz de 2 questões (6 + 4 pontos) e devolve a prova, já como ONLINE. */
    private Prova montarQuiz(Classe c) {
        Prova p = fx.prova(c, "10.00");
        var salvar = new QuizDto.Salvar(List.of(
                new QuizDto.QuestaoIn("2+2?", "MULTIPLA", new BigDecimal("6.00"), List.of(
                        new QuizDto.AlternativaIn("4", true),
                        new QuizDto.AlternativaIn("3", false),
                        new QuizDto.AlternativaIn("5", false))),
                new QuizDto.QuestaoIn("O céu é azul.", "VF", new BigDecimal("4.00"), List.of(
                        new QuizDto.AlternativaIn("Verdadeiro", true),
                        new QuizDto.AlternativaIn("Falso", false)))));
        quizService.salvarQuestoes(p.getId(), salvar);
        return p;
    }

    /** Encontra o id da alternativa por texto numa questão do quiz do aluno. */
    private Long altId(QuizAlunoDto.ParaResponder quiz, int idxQuestao, String texto) {
        return quiz.questoes().get(idxQuestao).alternativas().stream()
                .filter(a -> a.texto().equals(texto)).findFirst().orElseThrow().id();
    }

    @Test
    @TestSecurity(user = "aluno1", roles = {"ADMIN", "ALUNO"})
    @TestTransaction
    void autoCorrecaoGravaNotaEBloqueiaSegundaTentativa() {
        Classe c = fx.classe("Turma Quiz");
        Aluno a = fx.aluno("Aluno Um", c, null, false);
        fx.usuarioAluno("aluno1", a);
        Prova p = montarQuiz(c);

        // O aluno pega o quiz (sem gabarito) e responde: 1 certa (4) + 1 errada (Falso).
        QuizAlunoDto.ParaResponder quiz = quizAlunoService.obterParaResponder(p.getId());
        assertEquals(2, quiz.questoes().size());

        var req = new QuizAlunoDto.SubmeterRequest(List.of(
                new QuizAlunoDto.RespostaIn(quiz.questoes().get(0).id(), altId(quiz, 0, "4")),
                new QuizAlunoDto.RespostaIn(quiz.questoes().get(1).id(), altId(quiz, 1, "Falso"))));
        QuizAlunoDto.Resultado r = quizAlunoService.submeter(p.getId(), req);

        // Nota = 6 (acertou a de 6 pontos), 1 de 2 acertos.
        assertEquals(0, new BigDecimal("6.00").compareTo(r.nota()));
        assertEquals(1, r.acertos());
        assertEquals(2, r.total());

        // NotaProva gravada (alimenta boletim/ranking).
        var nota = notaRepository.find("prova.id = ?1 and aluno.id = ?2", p.getId(), a.getId()).firstResult();
        assertEquals(0, new BigDecimal("6.00").compareTo(nota.getNota()));

        // 2ª tentativa é bloqueada (400).
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> quizAlunoService.submeter(p.getId(), req));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "aluno2", roles = {"ADMIN", "ALUNO"})
    @TestTransaction
    void notaMaximaEhAplicadaAoAcertarTudo() {
        Classe c = fx.classe("Turma Quiz 2");
        Aluno a = fx.aluno("Aluno Dois", c, null, false);
        fx.usuarioAluno("aluno2", a);
        Prova p = montarQuiz(c);

        QuizAlunoDto.ParaResponder quiz = quizAlunoService.obterParaResponder(p.getId());
        var req = new QuizAlunoDto.SubmeterRequest(List.of(
                new QuizAlunoDto.RespostaIn(quiz.questoes().get(0).id(), altId(quiz, 0, "4")),
                new QuizAlunoDto.RespostaIn(quiz.questoes().get(1).id(), altId(quiz, 1, "Verdadeiro"))));
        QuizAlunoDto.Resultado r = quizAlunoService.submeter(p.getId(), req);

        assertEquals(2, r.acertos());
        assertEquals(0, new BigDecimal("10.00").compareTo(r.nota()));
        assertTrue(r.questoes().stream().allMatch(QuizAlunoDto.ResultadoQuestao::acertou));
    }
}
