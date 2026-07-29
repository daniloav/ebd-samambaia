package br.com.ice.ebd;

import br.com.ice.ebd.dto.SalvarNotasRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        LocalDate hoje = LocalDate.now();
        fx.presente(fx.aula(c, hoje), a); // presente na aula da data da prova
        Prova p = fx.prova(c, "10.00", hoje);

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
        LocalDate hoje = LocalDate.now();
        fx.presente(fx.aula(c, hoje), a); // presente na aula da data da prova
        Prova p = fx.prova(c, "10.00", hoje);

        provaService.salvarNotas(p.getId(), new SalvarNotasRequest(List.of(
                new SalvarNotasRequest.Item(a.getId(), new BigDecimal("8.5")))));

        var nota = provaService.obterNotas(p.getId()).itens().stream()
                .filter(i -> i.alunoId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("8.5").compareTo(nota.nota()));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void provaOfflineSoAceitaNotaDeQuemEstavaPresente() {
        Classe c = fx.classe("Turma Prova 3");
        Aluno presente = fx.aluno("Presente", c, null, false);
        Aluno ausente = fx.aluno("Ausente", c, null, false);
        LocalDate hoje = LocalDate.now();
        Aula aula = fx.aula(c, hoje);
        fx.presente(aula, presente);
        fx.presenca(aula, ausente, false); // faltou
        Prova p = fx.prova(c, "10.00", hoje);

        // a grade só lista o aluno presente
        var itens = provaService.obterNotas(p.getId()).itens();
        assertTrue(itens.stream().anyMatch(i -> i.alunoId().equals(presente.getId())));
        assertFalse(itens.stream().anyMatch(i -> i.alunoId().equals(ausente.getId())));

        // lançar nota para o ausente é rejeitado
        var req = new SalvarNotasRequest(List.of(
                new SalvarNotasRequest.Item(ausente.getId(), new BigDecimal("7.0"))));
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> provaService.salvarNotas(p.getId(), req));
        assertEquals(400, ex.getResponse().getStatus());

        // lançar nota para o presente funciona
        provaService.salvarNotas(p.getId(), new SalvarNotasRequest(List.of(
                new SalvarNotasRequest.Item(presente.getId(), new BigDecimal("7.0")))));
        var nota = provaService.obterNotas(p.getId()).itens().stream()
                .filter(i -> i.alunoId().equals(presente.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("7.0").compareTo(nota.nota()));
    }
}
