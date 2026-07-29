package br.com.ice.ebd;

import br.com.ice.ebd.dto.AulaComplementarRequest;
import br.com.ice.ebd.dto.AulaComplementarResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import br.com.ice.ebd.service.AulaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class AulaComplementarServiceTest {

    @Inject AulaService aulaService;
    @Inject AulaRepository aulaRepo;
    @Inject PresencaRepository presencaRepo;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void desdobrarEmpurraAgendaSeguinteMaisSeteDias() {
        Classe turma = fx.classe("Turma A");
        Aluno profAluno = fx.aluno("Professor Fulano", turma, null, false);
        Usuario prof = fx.usuarioProfessor("prof.fulano", profAluno);

        LocalDate d0 = LocalDate.of(2026, 8, 2); // domingo
        Aula origem = fx.aula(turma, d0);
        origem.setProfessor(prof);
        Aula a2 = fx.aula(turma, d0.plusDays(7));
        Aula a3 = fx.aula(turma, d0.plusDays(14));
        Aula a4 = fx.aula(turma, d0.plusDays(21));

        // uma presença já lançada numa aula que será empurrada (viaja pelo aula_id)
        fx.presente(a2, profAluno);
        Long a2Id = a2.getId();

        AulaComplementarResponse r = aulaService.complementar(origem.getId(),
                new AulaComplementarRequest(null, null));

        // 3 aulas empurradas
        assertEquals(3, r.aulasMovidas());

        // aula complementar criada no domingo seguinte, herdando tema (+continuação) e professor
        assertNotNull(r.aula());
        assertEquals(d0.plusDays(7), r.aula().data());
        assertEquals("Tema de teste (continuação)", r.aula().tema());
        assertEquals(prof.getId(), r.aula().professorId());

        // origem intacta; as seguintes cada uma +7 dias (sem violar a unique por turma+data)
        assertEquals(d0, aulaRepo.findById(origem.getId()).getData());
        assertEquals(d0.plusDays(14), aulaRepo.findById(a2.getId()).getData());
        assertEquals(d0.plusDays(21), aulaRepo.findById(a3.getId()).getData());
        assertEquals(d0.plusDays(28), aulaRepo.findById(a4.getId()).getData());

        // a presença continua atachada à mesma aula (agora em d0+14)
        assertEquals(1, presencaRepo.count("aula.id", a2Id));

        // 5 aulas no total na turma (4 originais + a complementar)
        assertEquals(5, aulaRepo.listarPorClasse(turma.getId()).size());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void desdobrarUltimaAulaNaoEmpurraNada() {
        Classe turma = fx.classe("Turma B");
        LocalDate d0 = LocalDate.of(2026, 8, 2);
        Aula unica = fx.aula(turma, d0);

        AulaComplementarResponse r = aulaService.complementar(unica.getId(),
                new AulaComplementarRequest("Parte 2", null));

        assertEquals(0, r.aulasMovidas());
        assertEquals(d0.plusDays(7), r.aula().data());
        assertEquals("Parte 2", r.aula().tema());
        assertNull(r.aula().professorId());
        assertEquals(2, aulaRepo.listarPorClasse(turma.getId()).size());
    }
}
