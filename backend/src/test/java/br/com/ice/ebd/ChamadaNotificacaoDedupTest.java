package br.com.ice.ebd;

import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.NotificacaoService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ChamadaNotificacaoDedupTest {

    @Inject ChamadaService chamadaService;
    @Inject NotificacaoService notificacaoService;
    @Inject Fixtures fx;

    private SalvarChamadaRequest req(SalvarChamadaRequest.Item... itens) {
        return new SalvarChamadaRequest(List.of(itens));
    }
    private SalvarChamadaRequest.Item pres(Long id) {
        return new SalvarChamadaRequest.Item(id, true, true, true, true);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void soNotificaEventosNovos() {
        Classe c = fx.classe("Turma Notif");
        Aluno a1 = fx.aluno("A1", c, "a1@ebd.test", true);
        Aluno a2 = fx.aluno("A2", c, "a2@ebd.test", true);
        Aula aula = fx.aula(c, LocalDate.now());

        // 1ª chamada: ambos presentes -> 2 e-mails
        chamadaService.salvarChamada(aula.getId(), req(pres(a1.getId()), pres(a2.getId())));
        assertEquals(2, notificacaoService.notificarChamada(aula.getId()));

        // re-salva igual e notifica de novo -> nenhum novo
        chamadaService.salvarChamada(aula.getId(), req(pres(a1.getId()), pres(a2.getId())));
        assertEquals(0, notificacaoService.notificarChamada(aula.getId()));

        // chega A3 e recebe presença -> só 1 novo
        Aluno a3 = fx.aluno("A3", c, "a3@ebd.test", true);
        chamadaService.salvarChamada(aula.getId(), req(pres(a1.getId()), pres(a2.getId()), pres(a3.getId())));
        assertEquals(1, notificacaoService.notificarChamada(aula.getId()));

        // muda o estado do A1 (deixa de trazer a Bíblia) -> re-notifica só ele
        chamadaService.salvarChamada(aula.getId(), req(
                new SalvarChamadaRequest.Item(a1.getId(), true, false, true, true),
                pres(a2.getId()), pres(a3.getId())));
        assertEquals(1, notificacaoService.notificarChamada(aula.getId()));
    }
}
