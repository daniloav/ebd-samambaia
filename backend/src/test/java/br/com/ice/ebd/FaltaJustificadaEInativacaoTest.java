package br.com.ice.ebd;

import br.com.ice.ebd.dto.ChamadaResponse;
import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.dto.VisitanteRequest;
import br.com.ice.ebd.dto.VisitanteResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.DesafiosService;
import br.com.ice.ebd.service.VisitanteService;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class FaltaJustificadaEInativacaoTest {

    @Inject ChamadaService chamadaService;
    @Inject DesafiosService desafiosService;
    @Inject VisitanteService visitanteService;
    @Inject AlunoRepository alunoRepository;
    @Inject MockMailbox mailbox;
    @Inject Fixtures fx;

    @BeforeEach
    void limpaCaixa() {
        mailbox.clear();
    }

    private SalvarChamadaRequest ausente(Long alunoId) {
        return new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(alunoId, false, false, false, false)));
    }

    /** Falta com justificativa do professor (marcada ao salvar a chamada). */
    private SalvarChamadaRequest ausenteJustificado(Long alunoId, String motivo) {
        return new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(alunoId, false, false, false, false, true, motivo)));
    }

    @Test
    @TestSecurity(user = "joao", roles = {"ADMIN", "ALUNO"})
    @TestTransaction
    void faltaJustificadaVale30PctNoRanking() {
        Classe c = fx.classe("Turma Justificada");
        Aluno a = fx.aluno("Joao", c, null, false);
        fx.usuarioAluno("joao", a); // o mesmo username do @TestSecurity resolve alunoIdLogado()
        Aula a1 = fx.aula(c, LocalDate.now().minusDays(14));
        Aula a2 = fx.aula(c, LocalDate.now().minusDays(7));

        chamadaService.salvarChamada(a1.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, false, false, false))));
        chamadaService.salvarChamada(a2.getId(), ausente(a.getId()));

        // Antes de justificar: só 1 presença conta.
        assertEquals(1.0, desafiosService.gerar(c.getId(), null, null).menosFaltou().get(0).valor());

        // Professor justifica a falta da aula 2 (ao salvar a chamada) → passa a valer 0,3.
        chamadaService.salvarChamada(a2.getId(), ausenteJustificado(a.getId(), "Viagem de trabalho"));

        DesafiosResponse d = desafiosService.gerar(c.getId(), null, null);
        assertEquals(1.3, d.menosFaltou().get(0).valor());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void inativaNaQuintaFaltaSeguidaEAvisaPorEmail() {
        Classe c = fx.classe("Turma Inativa");
        Aluno a = fx.aluno("Faltoso", c, "faltoso@teste.local", false);

        ChamadaResponse ultima = null;
        for (int i = 5; i >= 1; i--) { // 5 aulas, da mais antiga para a mais recente
            Aula aula = fx.aula(c, LocalDate.now().minusDays(i * 7L));
            ultima = chamadaService.salvarChamada(aula.getId(), ausente(a.getId()));
        }

        // Na 5ª falta seguida (>4) o aluno é inativado e um alerta é retornado.
        assertFalse(alunoRepository.findById(a.getId()).isAtivo());
        assertEquals(1, ultima.alertas().size());
        assertTrue(ultima.alertas().get(0).contains("Faltoso"));
        // E recebe o e-mail de aviso (assíncrono via EventBus).
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(1, mailbox.getMailMessagesSentTo("faltoso@teste.local").size()));
        assertTrue(mailbox.getMailMessagesSentTo("faltoso@teste.local").get(0)
                .getSubject().contains("Sentimos a sua falta"));
    }

    @Test
    @TestSecurity(user = "joao", roles = {"ADMIN", "ALUNO"})
    @TestTransaction
    void faltaJustificadaZeraSequenciaEEvitaInativacao() {
        Classe c = fx.classe("Turma Zera");
        Aluno a = fx.aluno("Joao", c, null, false);
        fx.usuarioAluno("joao", a);

        // 4 faltas seguidas (mais antigas → mais recentes), sem inativar ainda.
        Aula maisAntiga = null;
        for (int i = 5; i >= 2; i--) {
            Aula aula = fx.aula(c, LocalDate.now().minusDays(i * 7L));
            if (maisAntiga == null) maisAntiga = aula;
            chamadaService.salvarChamada(aula.getId(), ausente(a.getId()));
        }
        // Professor justifica a falta MAIS ANTIGA (via chamada) → quebra a sequência antes de chegar a 5.
        chamadaService.salvarChamada(maisAntiga.getId(), ausenteJustificado(a.getId(), "Estava doente"));

        // 5ª aula (mais recente): a sequência sem justificativa é só 4 → não inativa.
        Aula recente = fx.aula(c, LocalDate.now().minusDays(1));
        ChamadaResponse r = chamadaService.salvarChamada(recente.getId(), ausente(a.getId()));

        assertTrue(alunoRepository.findById(a.getId()).isAtivo());
        assertTrue(r.alertas().isEmpty());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void visitanteViraAlunoApos3AulasSeguidasERecebeBoasVindas() {
        Classe c = fx.classe("Turma Visitante");
        Aula a1 = fx.aula(c, LocalDate.now().minusDays(14));
        Aula a2 = fx.aula(c, LocalDate.now().minusDays(7));
        Aula a3 = fx.aula(c, LocalDate.now().minusDays(1));

        VisitanteRequest req = new VisitanteRequest("Maria Souza", "maria@teste.local", "(61) 99999-0000", null);
        assertNull(visitanteService.adicionar(a1.getId(), req).alerta());
        assertNull(visitanteService.adicionar(a2.getId(), req).alerta());
        VisitanteResponse terceira = visitanteService.adicionar(a3.getId(), req);

        // Na 3ª aula seguida, vira aluno.
        assertNotNull(terceira.alerta());
        assertTrue(terceira.alerta().contains("Maria Souza"));
        boolean virouAluno = alunoRepository.listarPorClasse(c.getId()).stream()
                .anyMatch(al -> al.getNome().equals("Maria Souza") && al.isAtivo());
        assertTrue(virouAluno);
        // E recebe o e-mail de boas-vindas como aluno (além dos de visitante).
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertTrue(mailbox.getMailMessagesSentTo("maria@teste.local").stream()
                        .anyMatch(m -> m.getSubject().contains("como aluno(a) da EBD"))));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void visitanteSemContatoNaoPromove() {
        Classe c = fx.classe("Turma Sem Contato");
        Aula a1 = fx.aula(c, LocalDate.now().minusDays(14));
        Aula a2 = fx.aula(c, LocalDate.now().minusDays(7));
        Aula a3 = fx.aula(c, LocalDate.now().minusDays(1));

        VisitanteRequest req = new VisitanteRequest("Sem Contato", null, null, null);
        visitanteService.adicionar(a1.getId(), req);
        visitanteService.adicionar(a2.getId(), req);
        VisitanteResponse terceira = visitanteService.adicionar(a3.getId(), req);

        assertNull(terceira.alerta());
        boolean virou = alunoRepository.listarPorClasse(c.getId()).stream()
                .anyMatch(al -> al.getNome().equals("Sem Contato"));
        assertFalse(virou);
    }
}
