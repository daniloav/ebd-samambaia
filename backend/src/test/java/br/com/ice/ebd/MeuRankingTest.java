package br.com.ice.ebd;

import br.com.ice.ebd.dto.MeuRankingResponse;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MeuRankingTest {

    @Inject ChamadaService chamadaService;
    @Inject DesafiosService desafiosService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "aluno.eu", roles = {"ALUNO", "ADMIN"})
    @TestTransaction
    void resumoTrazPodioEMinhaPosicao() {
        Classe c = fx.classe("Turma Ranking");
        Aluno a1 = fx.aluno("Primeiro Lugar", c, null, false);
        Aluno a2 = fx.aluno("Segundo Lugar", c, null, false);
        Aluno eu = fx.aluno("Eu Mesmo", c, null, false);
        fx.usuarioAluno("aluno.eu", eu); // liga o @TestSecurity ao aluno "eu"
        Aula aula = fx.aula(c, LocalDate.now());

        // a1 pontua mais (4 itens), a2 (2), eu (1)
        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a1.getId(), true, true, true, true),
                new SalvarChamadaRequest.Item(a2.getId(), true, true, false, false),
                new SalvarChamadaRequest.Item(eu.getId(), true, false, false, false))));

        MeuRankingResponse r = desafiosService.resumoDoAluno();

        assertEquals("Turma Ranking", r.turmaNome());
        assertEquals(3, r.totalParticipantes());
        assertEquals(3, r.podio().size());
        assertEquals(a1.getId(), r.podio().get(0).alunoId(), "1º deve ser o que pontuou mais");
        assertNotNull(r.minhaPosicao(), "deve trazer a posição do próprio aluno");
        assertEquals(eu.getId(), r.minhaPosicao().alunoId());
        assertTrue(r.minhaPosicao().eu());
        assertEquals(3, r.minhaPosicao().posicao());
    }
}
