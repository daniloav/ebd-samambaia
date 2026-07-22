package br.com.ice.ebd;

import br.com.ice.ebd.dto.ChamadaResponse;
import br.com.ice.ebd.dto.PresencaItem;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.service.ChamadaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ChamadaServiceTest {

    @Inject ChamadaService chamadaService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void salvarEObterChamadaPreservaOsItens() {
        Classe c = fx.classe("Turma Teste");
        Aluno a = fx.aluno("Fulano", c, null, false);
        Aula aula = fx.aula(c, LocalDate.now());

        var req = new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, true, false, true)));
        chamadaService.salvarChamada(aula.getId(), req);

        ChamadaResponse resp = chamadaService.obterChamada(aula.getId());
        PresencaItem item = resp.itens().stream()
                .filter(i -> i.alunoId().equals(a.getId())).findFirst().orElseThrow();

        assertTrue(item.presente());
        assertTrue(item.trouxeBiblia());
        assertFalse(item.trouxeRevista());
        assertTrue(item.estudouLicao());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void reenviarChamadaAtualizaOsValores() {
        Classe c = fx.classe("Turma Teste 2");
        Aluno a = fx.aluno("Ciclano", c, null, false);
        Aula aula = fx.aula(c, LocalDate.now());

        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, false, false, false))));
        // segunda gravação: muda para ausente + trouxe revista
        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), false, false, true, false))));

        PresencaItem item = chamadaService.obterChamada(aula.getId()).itens().stream()
                .filter(i -> i.alunoId().equals(a.getId())).findFirst().orElseThrow();
        assertFalse(item.presente());
        assertTrue(item.trouxeRevista());
    }
}
