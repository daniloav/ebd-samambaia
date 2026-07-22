package br.com.ice.ebd;

import br.com.ice.ebd.dto.DesafiosResponse;
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

        DesafiosResponse d = desafiosService.gerar(c.getId());

        assertEquals(1, d.totalAulas());
        // quem esteve presente lidera o "menos faltou"
        assertEquals(presente.getId(), d.menosFaltou().get(0).alunoId());
        assertEquals(1.0, d.menosFaltou().get(0).valor());
        // e o topo de "mais trouxe Bíblia" também é o presente (trouxe a Bíblia)
        assertEquals(presente.getId(), d.maisTrouxeBiblia().get(0).alunoId());
        assertEquals(1.0, d.maisTrouxeBiblia().get(0).valor());
    }
}
