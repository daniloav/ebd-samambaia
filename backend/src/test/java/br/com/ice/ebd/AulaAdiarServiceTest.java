package br.com.ice.ebd;

import br.com.ice.ebd.dto.AulaAdiarResponse;
import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.service.AulaService;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.DesafiosService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AulaAdiarServiceTest {

    @Inject AulaService aulaService;
    @Inject AulaRepository aulaRepo;
    @Inject ChamadaService chamadaService;
    @Inject DesafiosService desafiosService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void adiarMarcaAdiadaEmpurraAgendaECriaReposicao() {
        Classe turma = fx.classe("Turma Adiar");
        LocalDate d0 = LocalDate.of(2026, 8, 2); // domingo
        Aula origem = fx.aula(turma, d0);
        Aula a2 = fx.aula(turma, d0.plusDays(7));
        Aula a3 = fx.aula(turma, d0.plusDays(14));

        AulaAdiarResponse r = aulaService.adiar(origem.getId());

        // 2 aulas seguintes empurradas +7 dias
        assertEquals(2, r.aulasMovidas());
        assertEquals(d0.plusDays(14), aulaRepo.findById(a2.getId()).getData());
        assertEquals(d0.plusDays(21), aulaRepo.findById(a3.getId()).getData());

        // origem fica no lugar, marcada como adiada
        Aula origemDb = aulaRepo.findById(origem.getId());
        assertEquals(d0, origemDb.getData());
        assertTrue(origemDb.isAdiada());
        assertTrue(r.aulaAdiada().adiada());

        // reposição criada no domingo liberado, herdando o tema, e não adiada
        assertEquals(d0.plusDays(7), r.reposicao().data());
        assertEquals("Tema de teste", r.reposicao().tema());
        assertNull(r.reposicao().professorId());
        assertFalse(r.reposicao().adiada());

        // 4 aulas no total (3 originais + a reposição)
        assertEquals(4, aulaRepo.listarPorClasse(turma.getId()).size());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void aulaAdiadaNaoContaNoRanking() {
        Classe c = fx.classe("Turma Sem Pontuar Adiada");
        Aluno a = fx.aluno("Aluno", c, null, false);
        Aula normal = fx.aula(c, LocalDate.now().minusDays(7));
        Aula adiada = fx.aula(c, LocalDate.now().minusDays(14));
        adiada.setAdiada(true);

        // presente na normal; falta na adiada (que não deve penalizar ninguém)
        fx.presente(normal, a);
        fx.presenca(adiada, a, false);

        DesafiosResponse d = desafiosService.gerar(c.getId(), null, null);

        // Só a aula normal conta — a adiada some da pontuação e do retrospecto.
        assertEquals(1, d.totalAulas());
        assertEquals(1.0, d.menosFaltou().get(0).valor());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void chamadaEmAulaAdiadaEhBloqueada() {
        Classe c = fx.classe("Turma Chamada Adiada");
        Aluno a = fx.aluno("Aluno", c, null, false);
        Aula adiada = fx.aula(c, LocalDate.now());
        adiada.setAdiada(true);

        assertThrows(WebApplicationException.class, () ->
                chamadaService.salvarChamada(adiada.getId(), new SalvarChamadaRequest(List.of(
                        new SalvarChamadaRequest.Item(a.getId(), true, false, false, false)))));
    }
}
