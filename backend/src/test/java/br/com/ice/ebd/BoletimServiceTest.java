package br.com.ice.ebd;

import br.com.ice.ebd.dto.BoletimResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.dto.SalvarNotasRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.service.BoletimService;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.ProvaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BoletimServiceTest {

    @Inject ChamadaService chamadaService;
    @Inject ProvaService provaService;
    @Inject BoletimService boletimService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void boletimAgregaProvasEFrequenciaDoTrimestre() {
        Classe c = fx.classe("Turma Boletim");
        Aluno a = fx.aluno("Fulano", c, null, false);

        // 1º trimestre de 2025 (Jan–Mar)
        Aula aula = fx.aula(c, LocalDate.of(2025, 2, 15));
        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(List.of(
                new SalvarChamadaRequest.Item(a.getId(), true, true, false, false))));

        Prova p = fx.prova(c, "10.00", LocalDate.of(2025, 2, 20));
        provaService.salvarNotas(p.getId(), new SalvarNotasRequest(List.of(
                new SalvarNotasRequest.Item(a.getId(), new BigDecimal("9.0")))));

        BoletimResponse b = boletimService.gerar(a.getId(), 2025, 1);

        assertEquals(LocalDate.of(2025, 1, 1), b.periodoInicio());
        assertEquals(LocalDate.of(2025, 3, 31), b.periodoFim());
        assertEquals(1, b.provas().size());
        assertEquals(0, new BigDecimal("9.0").compareTo(b.provas().get(0).nota()));
        assertEquals(1, b.frequencia().totalAulas());
        assertEquals(1, b.frequencia().presencas());
        assertEquals(0, b.frequencia().faltas());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void trimestreSemRegistrosVemVazio() {
        Classe c = fx.classe("Turma Boletim 2");
        Aluno a = fx.aluno("Ciclano", c, null, false);

        BoletimResponse b = boletimService.gerar(a.getId(), 2025, 3);  // Jul–Set, sem dados
        assertEquals(0, b.provas().size());
        assertEquals(0, b.frequencia().totalAulas());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void aulaFuturaDoTrimestreNaoContaNoBoletim() {
        Classe c = fx.classe("Turma Boletim Futuro");
        Aluno a = fx.aluno("Beltrano", c, null, false);

        // Trimestre corrente: uma aula no início (já realizada) e outra no fim (pode ser futura).
        LocalDate hoje = LocalDate.now();
        int tri = (hoje.getMonthValue() - 1) / 3 + 1;
        int ano = hoje.getYear();
        LocalDate iniTri = LocalDate.of(ano, (tri - 1) * 3 + 1, 1);
        LocalDate fimTri = iniTri.plusMonths(3).minusDays(1);

        fx.aula(c, iniTri);   // <= hoje: conta
        fx.aula(c, fimTri);   // fim do trimestre: só conta se já passou

        BoletimResponse b = boletimService.gerar(a.getId(), ano, tri);

        long esperado = fimTri.isAfter(hoje) ? 1 : 2; // a aula futura não entra
        assertEquals(esperado, b.frequencia().totalAulas());
        assertEquals(esperado, b.frequencia().faltas()); // faltas não infla com aula futura
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void trimestreEncerradoContabilizaFrequencia() {
        Classe c = fx.classe("Turma Encerrada");
        Aluno a = fx.aluno("Ciclano", c, null, false);
        fx.aula(c, LocalDate.of(2025, 2, 15)); // 1º tri/2025 (encerrado), sem presença -> 0%

        BoletimResponse b = boletimService.gerar(a.getId(), 2025, 1);

        assertEquals(1, b.frequencia().totalAulas());
        assertEquals(1, b.frequencia().faltas());
    }
}
