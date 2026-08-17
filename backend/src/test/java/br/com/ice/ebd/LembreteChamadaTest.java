package br.com.ice.ebd;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.service.LembreteChamadaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lembrete horário ao professor quando a chamada do dia ainda não foi feita.
 * Cada caso cria a sua própria turma, então o lembrete das outras turmas do banco
 * de teste não interfere nas asserções (contamos só o que é desta turma).
 */
@QuarkusTest
class LembreteChamadaTest {

    @Inject LembreteChamadaService lembrete;
    @Inject Fixtures fx;

    /** Cria uma turma com 1 aluno ativo, 1 professor com e-mail e a aula de hoje. */
    private Aula aulaDeHojeCom(String sufixo, String emailProfessor) {
        Classe c = fx.classe("Turma Lembrete " + sufixo);
        fx.aluno("Aluno " + sufixo, c, null, false);
        Usuario prof = fx.usuario("prof.lembrete." + sufixo.toLowerCase(), Role.PROFESSOR, emailProfessor);
        prof.getClasses().add(c);
        Aula aula = fx.aula(c, LocalDate.now());
        aula.setProfessor(prof);
        return aula;
    }

    @Test
    @TestTransaction
    void aulaDeHojeSemChamadaCobraOProfessor() {
        Aula aula = aulaDeHojeCom("A", "prof.a@ebd.test");

        LembreteChamadaService.Resultado r = lembrete.enviarPendentes();

        assertTrue(r.turmas().contains(aula.getClasse().getNome()), "a aula sem chamada deve ser cobrada");
        assertTrue(r.enviados() >= 1);
        assertNotNull(aula.getChamadaCobradaEm(), "deve carimbar o disparo para a dedup");

        // 2º disparo na mesma hora não reenvia nada (dedup por hora)
        LembreteChamadaService.Resultado r2 = lembrete.enviarPendentes();
        assertTrue(r2.turmas().contains(aula.getClasse().getNome()), "continua pendente");
        assertEquals(0, r2.enviados(), "nenhuma aula é cobrada duas vezes na mesma hora");
    }

    @Test
    @TestTransaction
    void aulaComChamadaFeitaOuAdiadaNaoCobra() {
        // chamada já registrada (existe presença) — não cobra
        Aula comChamada = aulaDeHojeCom("B", "prof.b@ebd.test");
        fx.presente(comChamada, fx.aluno("Presente B", comChamada.getClasse(), null, false));

        // aula adiada — fora de toda cobrança
        Aula adiada = aulaDeHojeCom("C", "prof.c@ebd.test");
        adiada.setAdiada(true);

        LembreteChamadaService.Resultado r = lembrete.enviarPendentes();

        assertTrue(!r.turmas().contains(comChamada.getClasse().getNome()), "chamada feita não é cobrada");
        assertTrue(!r.turmas().contains(adiada.getClasse().getNome()), "aula adiada não é cobrada");
    }

    @Test
    @TestTransaction
    void aulaDeOutroDiaNaoCobra() {
        Classe c = fx.classe("Turma Lembrete D");
        fx.aluno("Aluno D", c, null, false);
        Usuario prof = fx.usuario("prof.lembrete.d", Role.PROFESSOR, "prof.d@ebd.test");
        prof.getClasses().add(c);
        Aula ontem = fx.aula(c, LocalDate.now().minusDays(1));
        ontem.setProfessor(prof);

        LembreteChamadaService.Resultado r = lembrete.enviarPendentes();

        assertTrue(!r.turmas().contains(c.getNome()), "só a aula do dia é cobrada");
        assertEquals(null, ontem.getChamadaCobradaEm());
    }
}
