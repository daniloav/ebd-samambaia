package br.com.ice.ebd;

import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.dto.ProvaRequest;
import br.com.ice.ebd.dto.ProvaResponse;
import br.com.ice.ebd.dto.QuizAlunoDto;
import br.com.ice.ebd.dto.QuizDto;
import br.com.ice.ebd.dto.RankingItem;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.repository.NotaProvaRepository;
import br.com.ice.ebd.service.DesafiosService;
import br.com.ice.ebd.service.ProvaService;
import br.com.ice.ebd.service.QuizAlunoService;
import br.com.ice.ebd.service.QuizService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RecuperacaoProvaTest {

    @Inject ProvaService provaService;
    @Inject QuizService quizService;
    @Inject QuizAlunoService quizAlunoService;
    @Inject DesafiosService desafiosService;
    @Inject NotaProvaRepository notaRepository;
    @Inject Fixtures fx;

    /** Recuperação da aula com 2 questões de 1 ponto (nota máxima 2 = 1 presença). */
    private ProvaResponse recuperacao(Classe c, Aula aula) {
        ProvaResponse p = provaService.criar(new ProvaRequest(c.getId(), "Recuperação", LocalDate.now(),
                new BigDecimal("10"), "RECUPERACAO", aula.getId(), null, null));
        quizService.salvarQuestoes(p.id(), new QuizDto.Salvar(List.of(
                new QuizDto.QuestaoIn("Quem construiu a arca?", "MULTIPLA", BigDecimal.ONE, List.of(
                        new QuizDto.AlternativaIn("Noé", true),
                        new QuizDto.AlternativaIn("Moisés", false),
                        new QuizDto.AlternativaIn("Davi", false))),
                new QuizDto.QuestaoIn("Quantos discípulos?", "MULTIPLA", BigDecimal.ONE, List.of(
                        new QuizDto.AlternativaIn("12", true),
                        new QuizDto.AlternativaIn("7", false))))));
        return p;
    }

    /** Responde marcando, por questão, a alternativa com o texto dado (ou a primeira errada). */
    private QuizAlunoDto.Resultado responder(Long provaId, boolean acertaArca, boolean acertaDiscipulos) {
        QuizAlunoDto.ParaResponder quiz = quizAlunoService.obterParaResponder(provaId);
        List<QuizAlunoDto.RespostaIn> respostas = quiz.questoes().stream().map(q -> {
            boolean arca = q.enunciado().contains("arca");
            String certa = arca ? "Noé" : "12";
            boolean acertar = arca ? acertaArca : acertaDiscipulos;
            Long alt = q.alternativas().stream()
                    .filter(a -> acertar == a.texto().equals(certa)).findFirst().orElseThrow().id();
            return new QuizAlunoDto.RespostaIn(q.id(), alt);
        }).toList();
        QuizAlunoDto.Resultado r = quizAlunoService.submeter(provaId, new QuizAlunoDto.SubmeterRequest(respostas));
        // A correção volta na ordem em que o aluno viu as questões (embaralhadas).
        assertEquals(respostas.stream().map(QuizAlunoDto.RespostaIn::questaoId).toList(),
                r.questoes().stream().map(QuizAlunoDto.ResultadoQuestao::questaoId).toList());
        return r;
    }

    private static double valor(List<RankingItem> ranking, Long alunoId) {
        return ranking.stream().filter(i -> i.alunoId().equals(alunoId)).findFirst().orElseThrow().valor();
    }

    @Test
    @TestSecurity(user = "rec.faltou", roles = {"ADMIN", "ALUNO"})
    @TestTransaction
    void tresTentativasValeAMelhorEViraPresencaProporcional() {
        Classe c = fx.classe("Turma Recuperação");
        Aluno a = fx.aluno("Faltou", c, null, false);
        fx.usuarioAluno("rec.faltou", a);
        Aula aula = fx.aula(c, LocalDate.now().minusDays(7));
        fx.presenca(aula, a, false);
        ProvaResponse p = recuperacao(c, aula);
        assertEquals(aula.getId(), p.aulaId());

        QuizAlunoDto.Resultado t1 = responder(p.id(), true, false); // 1 de 2
        assertEquals(1, t1.tentativa());
        assertEquals(3, t1.tentativasMax());
        assertTrue(t1.podeTentarDeNovo());
        assertEquals(0, new BigDecimal("0.50").compareTo(t1.presencaEquivalente()));
        // Gabarito já na 1ª tentativa.
        assertTrue(t1.questoes().stream().allMatch(q -> q.corretaId() != null));

        QuizAlunoDto.Resultado t2 = responder(p.id(), false, false); // 0: a melhor continua 1
        assertEquals(0, BigDecimal.ONE.compareTo(t2.melhorNota()));
        QuizAlunoDto.Resultado t3 = responder(p.id(), false, false);
        assertEquals(3, t3.tentativa());
        assertFalse(t3.podeTentarDeNovo());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> quizAlunoService.obterParaResponder(p.id()));
        assertEquals(400, ex.getResponse().getStatus());

        QuizAlunoDto.ProvaResumo resumo = quizAlunoService.listarMinhas().get(0);
        assertEquals("RESPONDIDA", resumo.status());
        assertEquals(3, resumo.tentativasUsadas());

        // Não vira nota: não grava NotaProva nem conta como prova no ranking...
        assertEquals(0, notaRepository.count("prova.id", p.id()));
        DesafiosResponse d = desafiosService.gerar(c.getId(), null, null);
        assertEquals(0, d.totalProvas());
        // ...e sim meia presença na aula que ele perdeu.
        assertEquals(0.5, valor(d.menosFaltou(), a.getId()));
        assertEquals(0.5, valor(d.classificacaoGeral(), a.getId()));
    }

    @Test
    @TestSecurity(user = "rec.justificou", roles = {"ADMIN", "ALUNO"})
    @TestTransaction
    void faltaJustificadaValeOMaiorEPresenteGanhaBonus() {
        Classe c = fx.classe("Turma Recuperação 2");
        Aluno a = fx.aluno("Justificou", c, null, false);
        fx.usuarioAluno("rec.justificou", a);
        Aula aula = fx.aula(c, LocalDate.now().minusDays(7));
        Presenca falta = fx.presenca(aula, a, false);
        falta.setJustificada(true);
        ProvaResponse p = recuperacao(c, aula);

        // Metade na recuperação não supera o 0,3 da justificada por muito: vale 0,5 no total.
        responder(p.id(), true, false);
        assertEquals(0.5, valor(desafiosService.gerar(c.getId(), null, null).menosFaltou(), a.getId()));

        // Gabaritou: 1 presença inteira (0,3 + 0,7), e não há por que tentar de novo.
        QuizAlunoDto.Resultado r = responder(p.id(), true, true);
        assertFalse(r.podeTentarDeNovo());
        assertEquals(1.0, valor(desafiosService.gerar(c.getId(), null, null).menosFaltou(), a.getId()));
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> quizAlunoService.obterParaResponder(p.id()));
        assertTrue(ex.getMessage().contains("nota máxima"));

        // Quem esteve presente também pode fazer: soma à presença.
        falta.setPresente(true);
        falta.setJustificada(false);
        assertEquals(2.0, valor(desafiosService.gerar(c.getId(), null, null).menosFaltou(), a.getId()));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void cadastroExigeAulaUnicaEMultiplaEscolha() {
        Classe c = fx.classe("Turma Recuperação 3");
        Aula aula = fx.aula(c, LocalDate.now().minusDays(7));

        WebApplicationException semAula = assertThrows(WebApplicationException.class,
                () -> provaService.criar(new ProvaRequest(c.getId(), "Sem aula", LocalDate.now(),
                        BigDecimal.TEN, "RECUPERACAO", null, null, null)));
        assertEquals(400, semAula.getResponse().getStatus());

        ProvaResponse p = recuperacao(c, aula);
        assertNotNull(p.id());
        WebApplicationException duplicada = assertThrows(WebApplicationException.class,
                () -> provaService.criar(new ProvaRequest(c.getId(), "De novo", LocalDate.now(),
                        BigDecimal.TEN, "RECUPERACAO", aula.getId(), null, null)));
        assertEquals(400, duplicada.getResponse().getStatus());

        WebApplicationException vf = assertThrows(WebApplicationException.class,
                () -> quizService.salvarQuestoes(p.id(), new QuizDto.Salvar(List.of(
                        new QuizDto.QuestaoIn("V ou F?", "VF", BigDecimal.ONE, List.of(
                                new QuizDto.AlternativaIn("Verdadeiro", true),
                                new QuizDto.AlternativaIn("Falso", false)))))));
        assertEquals(400, vf.getResponse().getStatus());

        // Nota da recuperação não se lança à mão.
        WebApplicationException lancar = assertThrows(WebApplicationException.class,
                () -> provaService.salvarNotas(p.id(), new br.com.ice.ebd.dto.SalvarNotasRequest(List.of())));
        assertEquals(400, lancar.getResponse().getStatus());
    }
}
