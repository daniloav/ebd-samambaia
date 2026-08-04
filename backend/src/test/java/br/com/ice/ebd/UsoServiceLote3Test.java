package br.com.ice.ebd;

import br.com.ice.ebd.dto.UsoResponse;
import br.com.ice.ebd.model.AcessoEvento;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.service.UsoService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cobre a lógica isolável do lote 3 do painel /uso: streak de semanas, pico e % fora do domingo. */
@QuarkusTest
class UsoServiceLote3Test {

    @Inject UsoService usoService;
    @Inject EntityManager em;
    @Inject Fixtures fx;

    /**
     * O ebd_test acumula acesso_evento commitados por outros testes (ex.: logins REST do
     * AuthResourceTest, fora de transação de teste). Zera as tabelas de evento antes de cada
     * caso para tornar as contagens determinísticas.
     */
    @BeforeEach
    void limparEventos() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("delete from uso_evento").executeUpdate();
            em.createNativeQuery("delete from acesso_evento").executeUpdate();
        });
    }

    /** Grava um evento de acesso (login) numa data/hora específica. */
    private void acesso(Usuario u, LocalDateTime quando) {
        AcessoEvento e = new AcessoEvento();
        e.setUsuario(u);
        e.setDataHora(quando);
        e.setUserAgent("teste");
        em.persist(e);
    }

    // ---------- A) Pico de atividade simultânea ----------

    @Test
    @TestTransaction
    void picoContaUsuariosDistintosNaMesmaJanela() {
        LocalDateTime agora = LocalDateTime.now();
        Usuario u1 = fx.usuario("pico.a", Role.PROFESSOR, null);
        Usuario u2 = fx.usuario("pico.b", Role.PROFESSOR, null);
        Usuario u3 = fx.usuario("pico.c", Role.PROFESSOR, null);
        // 3 usuários distintos ativos no mesmo instante (mesma janela de 15 min).
        acesso(u1, agora);
        acesso(u2, agora);
        acesso(u3, agora);
        // O mesmo usuário 2× não aumenta o pico.
        acesso(u1, agora);
        em.flush();

        UsoResponse r = usoService.gerar();
        assertEquals(3L, r.picoHoje());
        assertTrue(r.pico30d() >= 3L);
    }

    // ---------- E) Streak de semanas seguidas ----------

    @Test
    @TestTransaction
    void streakContaSemanasConsecutivasComAtividade() {
        LocalDateTime agora = LocalDateTime.now();
        Aluno aluno = fx.aluno("Aluno Streak", fx.classe("Turma S"), null, false);
        Usuario u = fx.usuarioAluno("aluno.streak", aluno);
        // Atividade nesta semana e na semana passada -> streak 2 (sem a de 2 semanas atrás).
        acesso(u, agora);
        acesso(u, agora.minusWeeks(1));
        em.flush();

        UsoResponse r = usoService.gerar();
        UsoResponse.StreakUsuario meu = r.streaks().stream()
                .filter(s -> s.username().equals("aluno.streak")).findFirst().orElse(null);
        assertTrue(meu != null, "aluno com atividade deveria aparecer nos streaks");
        assertEquals(2, meu.semanas());
    }

    // ---------- E) % da atividade fora do domingo ----------

    @Test
    @TestTransaction
    void pctForaDoDomingoIgnoraDomingo() {
        Usuario u = fx.usuario("fora.domingo", Role.PROFESSOR, null);
        // Âncora num domingo recente e conhecido (2026-08-02 é domingo).
        LocalDate domingo = LocalDate.of(2026, 8, 2);
        acesso(u, domingo.atTime(10, 0));            // domingo
        acesso(u, domingo.plusDays(1).atTime(10, 0)); // segunda
        acesso(u, domingo.plusDays(2).atTime(10, 0)); // terça
        acesso(u, domingo.plusDays(3).atTime(10, 0)); // quarta
        em.flush();

        // 3 de 4 fora do domingo = 75%.
        assertEquals(75.0, usoService.gerar().pctForaDoDomingo());
    }
}
