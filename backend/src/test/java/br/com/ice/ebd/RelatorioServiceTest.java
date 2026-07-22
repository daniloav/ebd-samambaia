package br.com.ice.ebd;

import br.com.ice.ebd.dto.RelatorioPresencaItem;
import br.com.ice.ebd.dto.RelatorioPresencaResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.RelatorioService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class RelatorioServiceTest {

    @Inject ChamadaService chamadaService;
    @Inject RelatorioService relatorioService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void agregaPresencasEFaltasNoPeriodo() {
        Classe c = fx.classe("Turma Rel");
        Aluno a = fx.aluno("Fulano", c, null, false);
        LocalDate hoje = LocalDate.now();
        Aula aula = fx.aula(c, hoje);

        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, true, true, false))));

        RelatorioPresencaResponse rel = relatorioService.gerar(hoje, hoje, c.getId());
        assertEquals(1, rel.totalAulas());

        RelatorioPresencaItem item = rel.itens().stream()
                .filter(i -> i.alunoId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals(1, item.presencas());
        assertEquals(0, item.faltas());
        assertEquals(1, item.trouxeBiblia());
    }
}
