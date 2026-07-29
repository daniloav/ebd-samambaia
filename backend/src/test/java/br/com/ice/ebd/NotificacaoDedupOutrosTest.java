package br.com.ice.ebd;

import br.com.ice.ebd.dto.SalvarNotasRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.service.NotificacaoService;
import br.com.ice.ebd.service.ProvaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class NotificacaoDedupOutrosTest {

    @Inject ProvaService provaService;
    @Inject NotificacaoService notificacaoService;
    @Inject Fixtures fx;

    private SalvarNotasRequest nota(Long alunoId, String valor) {
        return new SalvarNotasRequest(List.of(new SalvarNotasRequest.Item(alunoId, new BigDecimal(valor))));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void notaProvaSoReenviaSeMudar() {
        Classe c = fx.classe("Turma Nota Notif");
        Aluno a = fx.aluno("Aluno Nota", c, "nota@ebd.test", true);
        java.time.LocalDate hoje = java.time.LocalDate.now();
        fx.presente(fx.aula(c, hoje), a); // presente na aula da data da prova (offline)
        Prova p = fx.prova(c, "10.00", hoje);

        provaService.salvarNotas(p.getId(), nota(a.getId(), "8.0"));
        assertEquals(1, provaService.notificarNotas(p.getId()));   // 1ª vez
        assertEquals(0, provaService.notificarNotas(p.getId()));   // mesma nota — não reenvia

        provaService.salvarNotas(p.getId(), nota(a.getId(), "9.0"));
        assertEquals(1, provaService.notificarNotas(p.getId()));   // nota mudou — reenvia
    }

    @Test
    @TestTransaction
    void aniversarioNaoReenviaNoMesmoDia() {
        Classe c = fx.classe("Turma Aniv");
        Aluno a = fx.aluno("Aniversariante", c, "aniv@ebd.test", true);

        assertTrue(notificacaoService.enviarFelizAniversario(a));   // 1ª vez hoje
        assertFalse(notificacaoService.enviarFelizAniversario(a));  // mesmo dia — não reenvia
    }
}
