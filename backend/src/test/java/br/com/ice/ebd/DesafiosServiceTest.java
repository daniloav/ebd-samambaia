package br.com.ice.ebd;

import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.dto.RankingTurmasResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.DesafiosService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DesafiosServiceTest {

    @Inject ChamadaService chamadaService;
    @Inject DesafiosService desafiosService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void rankingRefleteAsPresencasEItens() {
        Classe c = fx.classe("Turma Desafio");
        Aluno presente = fx.aluno("Presente", c, null, false);
        Aluno faltoso = fx.aluno("Faltoso", c, null, false);
        Aula aula = fx.aula(c, LocalDate.now());

        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(presente.getId(), true, true, false, false),
                new SalvarChamadaRequest.Item(faltoso.getId(), false, false, false, false))));

        DesafiosResponse d = desafiosService.gerar(c.getId(), null, null);

        assertEquals(1, d.totalAulas());
        // quem esteve presente lidera o "menos faltou"
        assertEquals(presente.getId(), d.menosFaltou().get(0).alunoId());
        assertEquals(1.0, d.menosFaltou().get(0).valor());
        // e o topo de "mais trouxe Bíblia" também é o presente (trouxe a Bíblia)
        assertEquals(presente.getId(), d.maisTrouxeBiblia().get(0).alunoId());
        assertEquals(1.0, d.maisTrouxeBiblia().get(0).valor());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void aulasFuturasNaoEntramNoRanking() {
        Classe c = fx.classe("Turma Futuro");
        Aluno a = fx.aluno("Aluno", c, null, false);
        Aula passada = fx.aula(c, LocalDate.now().minusDays(7));
        Aula futura = fx.aula(c, LocalDate.now().plusDays(7));

        // Presença registrada na aula passada e também na futura (ex.: chamada adiantada).
        chamadaService.salvarChamada(passada.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, false, false, false))));
        chamadaService.salvarChamada(futura.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, false, false, false))));

        DesafiosResponse d = desafiosService.gerar(c.getId(), null, null);

        // Só a aula passada conta: 1 aula e 1 presença (a futura é ignorada).
        assertEquals(1, d.totalAulas());
        assertEquals(1.0, d.menosFaltou().get(0).valor());
    }
    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void rankingFiltraPorTrimestre() {
        Classe c = fx.classe("Turma Trimestre");
        Aluno a = fx.aluno("Aluno Tri", c, null, false);
        // Uma aula no 1º trimestre e outra no 2º (ambas no passado, contam).
        Aula q1 = fx.aula(c, LocalDate.of(2026, 2, 10));
        Aula q2 = fx.aula(c, LocalDate.of(2026, 5, 10));
        chamadaService.salvarChamada(q1.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, false, false, false))));
        chamadaService.salvarChamada(q2.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, false, false, false))));

        // Sem filtro: as duas aulas contam.
        assertEquals(2, desafiosService.gerar(c.getId(), null, null).totalAulas());
        // 1º trimestre de 2026: só a aula de fevereiro.
        DesafiosResponse t1 = desafiosService.gerar(c.getId(), 2026, 1);
        assertEquals(1, t1.totalAulas());
        assertEquals(1.0, t1.menosFaltou().get(0).valor());
        // 2º trimestre de 2026: só a aula de maio.
        assertEquals(1, desafiosService.gerar(c.getId(), 2026, 2).totalAulas());
        // 4º trimestre de 2026 (sem aulas): nenhuma.
        assertEquals(0, desafiosService.gerar(c.getId(), 2026, 4).totalAulas());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void rankingPorTurmaUsaMediaPorAluno() {
        // Turma grande (3 alunos), só 1 presença → média baixa por aluno.
        Classe grande = fx.classe("Turma Grande RT");
        Aluno g1 = fx.aluno("G1", grande, null, false);
        fx.aluno("G2", grande, null, false);
        fx.aluno("G3", grande, null, false);
        Aula aulaG = fx.aula(grande, LocalDate.now());
        chamadaService.salvarChamada(aulaG.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(g1.getId(), true, false, false, false))));

        // Turma pequena (1 aluno) muito engajada: presença + Bíblia + revista + lição.
        Classe pequena = fx.classe("Turma Pequena RT");
        Aluno p1 = fx.aluno("P1", pequena, null, false);
        Aula aulaP = fx.aula(pequena, LocalDate.now());
        chamadaService.salvarChamada(aulaP.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(p1.getId(), true, true, true, true))));

        RankingTurmasResponse r = desafiosService.gerarPorTurma(null, null);
        var itGrande = r.turmas().stream().filter(t -> t.classeId().equals(grande.getId())).findFirst().orElseThrow();
        var itPequena = r.turmas().stream().filter(t -> t.classeId().equals(pequena.getId())).findFirst().orElseThrow();

        // Grande: 1 pt total / 3 alunos = 0,33 de média.
        assertEquals(1.0, itGrande.total());
        assertEquals(3, itGrande.alunos());
        assertEquals(0.33, itGrande.valor());
        // Pequena: 4 pts total / 1 aluno = 4,0 de média.
        assertEquals(4.0, itPequena.total());
        assertEquals(1, itPequena.alunos());
        assertEquals(4.0, itPequena.valor());
        // A turma pequena e engajada fica à frente da grande, apesar de ter menos gente.
        assertTrue(itPequena.posicao() < itGrande.posicao());
    }
}
