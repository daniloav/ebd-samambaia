package br.com.ice.ebd;

import br.com.ice.ebd.dto.UsoResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.model.UsoEvento;
import br.com.ice.ebd.repository.PresencaRepository;
import br.com.ice.ebd.service.AcessoService;
import br.com.ice.ebd.service.UsoService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cobre o lote 2 do painel /uso: D (uso por funcionalidade) e F (professores/gestão). */
@QuarkusTest
class UsoServiceLote2Test {

    @Inject UsoService usoService;
    @Inject AcessoService acessoService;
    @Inject PresencaRepository presencaRepo;
    @Inject Fixtures fx;

    // ---------- D) Uso por funcionalidade ----------

    @Test
    @TestTransaction
    void featureMaisUsadaSomaPageViewsESeparaCliques() {
        Classe turma = fx.classe("Turma D");
        Aluno a = fx.aluno("Fulano", turma, null, false);
        fx.usuarioAluno("fulano", a);

        // 3 aberturas de "chamada", 1 de "desafios", e 2 cliques de export.
        acessoService.registrarEvento("fulano", "chamada", UsoEvento.Acao.ABRIR);
        acessoService.registrarEvento("fulano", "chamada", UsoEvento.Acao.ABRIR);
        acessoService.registrarEvento("fulano", "chamada", UsoEvento.Acao.ABRIR);
        acessoService.registrarEvento("fulano", "desafios", UsoEvento.Acao.ABRIR);
        acessoService.registrarEvento("fulano", "export-pdf-relatorio", UsoEvento.Acao.CLICAR);
        acessoService.registrarEvento("fulano", "export-pdf-relatorio", UsoEvento.Acao.CLICAR);

        UsoResponse r = usoService.gerar();

        Map<String, Long> features = r.featuresMaisUsadas().stream()
                .collect(Collectors.toMap(UsoResponse.Contagem::rotulo, UsoResponse.Contagem::quantidade));
        assertEquals(3L, features.get("chamada"));
        assertEquals(1L, features.get("desafios"));
        // A tela mais aberta vem primeiro (ordenado desc).
        assertEquals("chamada", r.featuresMaisUsadas().get(0).rotulo());
        // Cliques não entram nas "telas abertas".
        assertTrue(features.get("export-pdf-relatorio") == null);

        Map<String, Long> acoes = r.acoesNotaveis().stream()
                .collect(Collectors.toMap(UsoResponse.Contagem::rotulo, UsoResponse.Contagem::quantidade));
        assertEquals(2L, acoes.get("export-pdf-relatorio"));
        assertTrue(acoes.get("chamada") == null);
    }

    @Test
    @TestTransaction
    void recursoVazioEhIgnorado() {
        Classe turma = fx.classe("Turma D2");
        Aluno a = fx.aluno("Ciclano", turma, null, false);
        fx.usuarioAluno("ciclano", a);

        acessoService.registrarEvento("ciclano", "  ", UsoEvento.Acao.ABRIR);
        acessoService.registrarEvento("ciclano", null, UsoEvento.Acao.ABRIR);

        UsoResponse r = usoService.gerar();
        assertTrue(r.featuresMaisUsadas().isEmpty());
    }

    // ---------- F) Chamada no prazo × atrasada ----------

    @Test
    @TestTransaction
    void chamadaNoPrazoVsAtrasadaVsSemData() {
        Classe turma = fx.classe("Turma F");
        Aluno a = fx.aluno("Beltrano", turma, null, false);

        LocalDate dia = LocalDate.of(2026, 6, 7); // domingo

        // Aula 1: registrada no mesmo dia -> no prazo.
        Aula aula1 = fx.aula(turma, dia);
        Presenca p1 = fx.presente(aula1, a);
        p1.setRegistradaEm(dia.atTime(10, 30));

        // Aula 2: registrada 2 dias depois -> atrasada.
        Aula aula2 = fx.aula(turma, dia.plusDays(7));
        Presenca p2 = fx.presente(aula2, a);
        p2.setRegistradaEm(dia.plusDays(9).atTime(20, 0));

        // Aula 3: presença sem carimbo (histórico) -> sem data.
        Aula aula3 = fx.aula(turma, dia.plusDays(14));
        Presenca p3 = fx.presente(aula3, a);
        p3.setRegistradaEm(null);

        presencaRepo.flush();

        UsoResponse.ChamadaPrazo cp = usoService.gerar().chamadaPrazo();
        assertEquals(1L, cp.noPrazo());
        assertEquals(1L, cp.atrasadas());
        assertEquals(1L, cp.semData());
        assertEquals(50.0, cp.pctNoPrazo()); // 1 de 2 com data
    }

    // ---------- F) Cobertura de turmas na semana ----------

    @Test
    @TestTransaction
    void coberturaMarcaTurmaComChamadaNaSemana() {
        Classe comChamada = fx.classe("Turma Coberta");
        fx.classe("Turma Pendente");
        Aluno a = fx.aluno("Sicrano", comChamada, null, false);

        // Aula nesta semana (hoje) com presença -> turma coberta.
        Aula aula = fx.aula(comChamada, LocalDate.now());
        fx.presente(aula, a);
        presencaRepo.flush();

        Map<String, Boolean> cobertura = usoService.gerar().coberturaTurmas().stream()
                .collect(Collectors.toMap(UsoResponse.CoberturaTurma::turma, UsoResponse.CoberturaTurma::cobriu));
        assertEquals(Boolean.TRUE, cobertura.get("Turma Coberta"));
        assertEquals(Boolean.FALSE, cobertura.get("Turma Pendente"));
    }
}
