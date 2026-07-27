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

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void professorNaoContabilizaNaAulaQueDeu() {
        Classe c = fx.classe("Turma Professor");
        Aluno membro = fx.aluno("Membro Comum", c, null, false);
        Aluno profAluno = fx.aluno("Membro Professor", c, null, false);
        Usuario prof = fx.usuarioProfessor("prof.turma", profAluno);

        LocalDate hoje = LocalDate.now();
        Aula aulaDoProf = fx.aula(c, hoje.minusDays(7));
        aulaDoProf.setProfessor(prof); // este professor deu esta aula
        Aula aulaNormal = fx.aula(c, hoje.minusDays(1));

        // marca todos presentes nas duas aulas
        chamadaService.salvarChamada(aulaDoProf.getId(),
                new SalvarChamadaRequest(List.of(pres(membro.getId()), pres(profAluno.getId()))));
        chamadaService.salvarChamada(aulaNormal.getId(),
                new SalvarChamadaRequest(List.of(pres(membro.getId()), pres(profAluno.getId()))));

        // na chamada da aula que ele deu, o profAluno aparece desabilitado
        ChamadaResponse ch = chamadaService.obterChamada(aulaDoProf.getId());
        boolean flag = ch.itens().stream().filter(i -> i.alunoId().equals(profAluno.getId()))
                .findFirst().orElseThrow().professorDaAula();
        assertTrue(flag, "o aluno-professor deve vir marcado como professorDaAula");

        DesafiosResponse d = desafiosService.gerar(c.getId());
        // membro comum: 2 presenças; professor: só 1 (a aula que ele deu não conta)
        assertEquals(2.0, valorMenosFaltou(d, membro.getId()));
        assertEquals(1.0, valorMenosFaltou(d, profAluno.getId()));
    }
}
