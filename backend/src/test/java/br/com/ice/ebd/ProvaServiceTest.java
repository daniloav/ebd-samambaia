package br.com.ice.ebd;

import br.com.ice.ebd.dto.SalvarNotasRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.service.ProvaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ProvaServiceTest {

    @Inject ProvaService provaService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void notaAcimaDaMaximaEhRejeitada() {
        Classe c = fx.classe("Turma Prova");
        Aluno a = fx.aluno("Fulano", c, null, false);
        Prova p = fx.prova(c, "10.00");

        var req = new SalvarNotasRequest(List.of(
                new SalvarNotasRequest.Item(a.getId(), new BigDecimal("11.0"))));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> provaService.salvarNotas(p.getId(), req));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void notaValidaEhGravadaERecuperada() {
        Classe c = fx.classe("Turma Prova 2");
        Aluno a = fx.aluno("Ciclano", c, null, false);
        Prova p = fx.prova(c, "10.00");

        provaService.salvarNotas(p.getId(), new SalvarNotasRequest(List.of(
                new SalvarNotasRequest.Item(a.getId(), new BigDecimal("8.5")))));

        var nota = provaService.obterNotas(p.getId()).itens().stream()
                .filter(i -> i.alunoId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("8.5").compareTo(nota.nota()));
    }
}
