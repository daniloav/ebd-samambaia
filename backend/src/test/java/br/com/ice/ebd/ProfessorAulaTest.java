package br.com.ice.ebd;

import br.com.ice.ebd.dto.ChamadaResponse;
import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Usuario;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProfessorAulaTest {

    @Inject ChamadaService chamadaService;
    @Inject DesafiosService desafiosService;
    @Inject Fixtures fx;

    private SalvarChamadaRequest.Item pres(Long id) {
        return new SalvarChamadaRequest.Item(id, true, true, false, false);
    }
    private double valorMenosFaltou(DesafiosResponse d, Long alunoId) {
        return d.menosFaltou().stream().filter(i -> i.alunoId().equals(alunoId))
                .findFirst().map(i -> i.valor()).orElse(0.0);
    }
    private boolean flagProfessor(ChamadaResponse ch, Long alunoId) {
        return ch.itens().stream().filter(i -> i.alunoId().equals(alunoId))
                .findFirst().orElseThrow().professorDaAula();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void professorSoNaoContaNosDiasEmQueDaAula() {
        Classe turmaA = fx.classe("Turma A");   // onde o professor também é aluno
        Classe turmaB = fx.classe("Turma B");   // outra turma onde ele dá aula
        Aluno membro = fx.aluno("Membro Comum", turmaA, null, false);
        Aluno profAluno = fx.aluno("Membro Professor", turmaA, null, false);
        Usuario prof = fx.usuarioProfessor("prof.turma", profAluno);

        LocalDate diaLivre = LocalDate.now().minusDays(14); // não dá aula neste dia
        LocalDate diaEnsinaA = LocalDate.now().minusDays(7); // dá aula na própria turma A
        LocalDate diaEnsinaB = LocalDate.now().minusDays(1); // dá aula na turma B (mesmo dia da chamada de A)

        Aula aulaLivre = fx.aula(turmaA, diaLivre);
        Aula aulaEnsinaA = fx.aula(turmaA, diaEnsinaA);
        aulaEnsinaA.setProfessor(prof); // ele deu esta aula
        Aula aulaA_noDiaB = fx.aula(turmaA, diaEnsinaB); // aula da turma A neste dia (outro dá)
        Aula aulaB = fx.aula(turmaB, diaEnsinaB);
        aulaB.setProfessor(prof); // ele dá aula na turma B neste mesmo dia

        // salva a chamada das 3 aulas da turma A, marcando ambos presentes
        chamadaService.salvarChamada(aulaLivre.getId(),
                new SalvarChamadaRequest(List.of(pres(membro.getId()), pres(profAluno.getId()))));
        chamadaService.salvarChamada(aulaEnsinaA.getId(),
                new SalvarChamadaRequest(List.of(pres(membro.getId()), pres(profAluno.getId()))));
        chamadaService.salvarChamada(aulaA_noDiaB.getId(),
                new SalvarChamadaRequest(List.of(pres(membro.getId()), pres(profAluno.getId()))));

        // dia livre: conta como aluno normal (habilitado)
        assertFalse(flagProfessor(chamadaService.obterChamada(aulaLivre.getId()), profAluno.getId()),
                "num dia sem aula dele, o professor conta como aluno");
        // dia em que deu aula na própria turma A: desabilitado
        assertTrue(flagProfessor(chamadaService.obterChamada(aulaEnsinaA.getId()), profAluno.getId()),
                "no dia em que dá aula, fica desabilitado");
        // dia em que deu aula na turma B: desabilitado também na chamada da turma A
        assertTrue(flagProfessor(chamadaService.obterChamada(aulaA_noDiaB.getId()), profAluno.getId()),
                "dando aula em outra turma no mesmo dia, também fica desabilitado na turma A");

        DesafiosResponse d = desafiosService.gerar(turmaA.getId(), null, null);
        // membro comum: 3 presenças; professor: só 1 (só o dia livre conta)
        assertEquals(3.0, valorMenosFaltou(d, membro.getId()));
        assertEquals(1.0, valorMenosFaltou(d, profAluno.getId()));
    }
}
