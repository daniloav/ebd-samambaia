package br.com.ice.ebd;

import br.com.ice.ebd.dto.AlunoRequest;
import br.com.ice.ebd.dto.RelatorioInativadosResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.AlunoInativacao;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.MotivoInativacao;
import br.com.ice.ebd.repository.AlunoInativacaoRepository;
import br.com.ice.ebd.service.AlunoService;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.RelatorioInativadosService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Relatório de alunos inativados: o que entra no histórico e como ele é lido. */
@QuarkusTest
class RelatorioInativadosTest {

    @Inject ChamadaService chamadaService;
    @Inject AlunoService alunoService;
    @Inject RelatorioInativadosService relatorio;
    @Inject AlunoInativacaoRepository inativacaoRepository;
    @Inject Fixtures fx;

    private SalvarChamadaRequest ausente(Long alunoId) {
        return new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(alunoId, false, false, false, false)));
    }

    private RelatorioInativadosResponse.Item item(RelatorioInativadosResponse r, Long alunoId) {
        return r.itens().stream().filter(i -> i.alunoId().equals(alunoId)).findFirst().orElseThrow();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void inativacaoAutomaticaEntraComMotivoFaltasEUltimaPresenca() {
        Classe c = fx.classe("Turma Inativados Auto");
        Aluno a = fx.aluno("Sumido", c, null, false);

        LocalDate presenca = LocalDate.now().minusDays(42);
        chamadaService.salvarChamada(fx.aula(c, presenca).getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, false, false, false))));
        for (int i = 5; i >= 1; i--) { // 5 faltas seguidas → inativa na última
            chamadaService.salvarChamada(fx.aula(c, LocalDate.now().minusDays(i * 7L)).getId(), ausente(a.getId()));
        }

        RelatorioInativadosResponse r = relatorio.gerar(null, null, c.getId(), false);

        assertEquals(1, r.total());
        assertEquals(1, r.aindaInativos());
        assertEquals(1, r.porFaltasSeguidas());
        RelatorioInativadosResponse.Item i = item(r, a.getId());
        assertEquals(MotivoInativacao.FALTAS_SEGUIDAS, i.motivo());
        assertEquals(5, i.faltasSeguidas());
        assertNotNull(i.inativadoEm());
        assertNull(i.reativadoEm());
        assertEquals(presenca, i.ultimaPresenca()); // última aula em que apareceu — para a busca pastoral
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void inativacaoManualEReativacaoPeloCadastro() {
        Classe c = fx.classe("Turma Inativados Manual");
        Aluno a = fx.aluno("Mudou de cidade", c, null, false);

        alunoService.atualizar(a.getId(), new AlunoRequest(
                a.getNome(), c.getId(), null, null, null, false, false, null));

        RelatorioInativadosResponse r = relatorio.gerar(null, null, c.getId(), false);
        assertEquals(1, r.total());
        assertEquals(1, r.manuais());
        assertEquals(MotivoInativacao.MANUAL, item(r, a.getId()).motivo());
        assertEquals("admin", item(r, a.getId()).inativadoPor());

        // Voltou: o episódio é fechado e sai da lista padrão (só quem está inativo).
        alunoService.atualizar(a.getId(), new AlunoRequest(
                a.getNome(), c.getId(), null, null, null, false, true, null));

        assertEquals(0, relatorio.gerar(null, null, c.getId(), false).total());

        RelatorioInativadosResponse comVoltas = relatorio.gerar(null, null, c.getId(), true);
        assertEquals(1, comVoltas.total());
        assertEquals(1, comVoltas.reativados());
        assertNotNull(item(comVoltas, a.getId()).reativadoEm());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void episodioSemDataSoApareceQuandoNaoHaFiltroDePeriodo() {
        Classe c = fx.classe("Turma Inativados Legado");
        Aluno a = fx.aluno("Antigo", c, null, false);
        a.setAtivo(false);
        AlunoInativacao legado = new AlunoInativacao(); // como o histórico importado pela V30
        legado.setAluno(a);
        legado.setMotivo(MotivoInativacao.NAO_REGISTRADO);
        inativacaoRepository.persist(legado);

        RelatorioInativadosResponse aberto = relatorio.gerar(null, null, c.getId(), false);
        assertEquals(1, aberto.total());
        assertTrue(aberto.periodoAberto());
        assertNull(item(aberto, a.getId()).inativadoEm());
        assertEquals(1, aberto.semDataRegistrada());

        RelatorioInativadosResponse comPeriodo = relatorio.gerar(
                LocalDate.now().minusYears(1), LocalDate.now(), c.getId(), false);
        assertEquals(0, comPeriodo.total());
        assertEquals(1, comPeriodo.semDataRegistrada()); // a tela avisa que existem sem data
    }
}
