package br.com.ice.ebd;

import br.com.ice.ebd.dto.RelatorioMensalResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.service.RelatorioMensalService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relatório geral de presença por mês. Como o banco de teste tem outras turmas,
 * cada caso filtra explicitamente pelas turmas que criou.
 */
@QuarkusTest
class RelatorioMensalServiceTest {

    @Inject RelatorioMensalService service;
    @Inject Fixtures fx;

    private static final LocalDate D1 = LocalDate.of(2026, 3, 1);  // domingo
    private static final LocalDate D2 = LocalDate.of(2026, 3, 8);

    private RelatorioMensalResponse.LinhaTurma linha(RelatorioMensalResponse r, Long classeId) {
        return r.porTurma().stream().filter(l -> l.classeId().equals(classeId)).findFirst().orElseThrow();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void consolidaAsTurmasEscolhidasEMontaAseriePorDomingo() {
        Classe a = fx.classe("Mensal A");
        Classe b = fx.classe("Mensal B");
        Aluno a1 = fx.aluno("A1", a, null, false);
        Aluno a2 = fx.aluno("A2", a, null, false);
        Aluno b1 = fx.aluno("B1", b, null, false);

        Aula aulaA1 = fx.aula(a, D1);
        fx.presenca(aulaA1, a1, true);
        fx.presenca(aulaA1, a2, false);      // 1 de 2 → 50%
        Aula aulaA2 = fx.aula(a, D2);
        fx.presenca(aulaA2, a1, true);
        fx.presenca(aulaA2, a2, true);       // 2 de 2 → 100%
        Aula aulaB1 = fx.aula(b, D1);
        fx.presenca(aulaB1, b1, true);

        RelatorioMensalResponse r = service.gerar(2026, 3, List.of(a.getId(), b.getId()));

        // Totais: 4 presenças, 1 falta em 3 aulas → 80%
        assertEquals(3, r.totais().aulas());
        assertEquals(3, r.totais().aulasComChamada());
        assertEquals(4, r.totais().presencas());
        assertEquals(1, r.totais().faltas());
        assertEquals(80.0, r.totais().percentualPresenca());
        assertEquals(3, r.totais().alunosAtivos());
        assertEquals("Março de 2026", r.periodoLabel());

        // Quebra por turma
        assertEquals(75.0, linha(r, a.getId()).totais().percentualPresenca());  // 3 de 4
        assertEquals(100.0, linha(r, b.getId()).totais().percentualPresenca()); // 1 de 1

        // Série: um ponto por domingo com aula, com o valor de cada turma (gráfico agrupado)
        assertEquals(2, r.serie().size());
        RelatorioMensalResponse.PontoSerie p1 = r.serie().get(0);
        assertEquals("01/03", p1.rotulo());
        assertEquals(D1, p1.data());
        assertEquals(2, p1.totais().presencas());
        assertEquals(2, p1.porTurma().size());
        assertEquals(50.0, p1.porTurma().stream()
                .filter(v -> v.classeId().equals(a.getId())).findFirst().orElseThrow().percentualPresenca());
        assertEquals("08/03", r.serie().get(1).rotulo());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void aulaAdiadaEOutroMesFicamDeFora() {
        Classe c = fx.classe("Mensal Adiada");
        Aluno al = fx.aluno("Aluno Adiada", c, null, false);

        Aula normal = fx.aula(c, D1);
        fx.presenca(normal, al, true);
        Aula adiada = fx.aula(c, D2);
        adiada.setAdiada(true);
        fx.presenca(adiada, al, true);
        Aula outroMes = fx.aula(c, LocalDate.of(2026, 4, 5));
        fx.presenca(outroMes, al, true);

        RelatorioMensalResponse r = service.gerar(2026, 3, List.of(c.getId()));

        assertEquals(1, r.totais().aulas(), "só a aula válida do mês entra");
        assertEquals(1, r.totais().presencas());
        assertEquals(1, r.serie().size());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void anoInteiroAgrupaAseriePorMes() {
        Classe c = fx.classe("Mensal Ano");
        Aluno al = fx.aluno("Aluno Ano", c, null, false);
        fx.presenca(fx.aula(c, D1), al, true);                          // março
        fx.presenca(fx.aula(c, LocalDate.of(2026, 4, 5)), al, false);   // abril

        RelatorioMensalResponse r = service.gerar(2026, null, List.of(c.getId()));

        assertEquals("Ano de 2026", r.periodoLabel());
        assertEquals(LocalDate.of(2026, 1, 1), r.inicio());
        assertEquals(LocalDate.of(2026, 12, 31), r.fim());
        assertEquals(2, r.serie().size());
        assertEquals("Mar", r.serie().get(0).rotulo());
        assertEquals("Abr", r.serie().get(1).rotulo());
        assertEquals(50.0, r.totais().percentualPresenca());
    }

    @Test
    @TestSecurity(user = "prof.mensal", roles = "PROFESSOR")
    @TestTransaction
    void professorSoGeraDasTurmasDele() {
        Classe minha = fx.classe("Mensal Do Professor");
        Classe outra = fx.classe("Mensal De Outro");
        fx.aluno("Aluno Prof", minha, null, false);
        var prof = fx.usuario("prof.mensal", br.com.ice.ebd.model.Role.PROFESSOR, null);
        prof.getClasses().add(minha);

        // Sem escolher turma, vem só a turma vinculada a ele.
        RelatorioMensalResponse r = service.gerar(2026, 3, List.of());
        assertEquals(1, r.turmas().size());
        assertEquals(minha.getId(), r.turmas().get(0).classeId());

        // Pedir a turma de outro professor é barrado pelo escopo.
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                        jakarta.ws.rs.ForbiddenException.class,
                        () -> service.gerar(2026, 3, List.of(outra.getId())))
                .getMessage().contains("acesso"));
    }
}
