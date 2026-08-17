package br.com.ice.ebd;

import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.DiaSemanaLeitura;
import br.com.ice.ebd.model.TextoBiblicoAula;
import br.com.ice.ebd.service.BibliaOnlineService;
import br.com.ice.ebd.service.LeituraDiariaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leitura bíblica diária: às 12h sai o texto do dia da semana corrente, referente às aulas
 * da <b>semana seguinte</b> (as leituras antecedem a lição).
 *
 * <p>Como o batch varre o banco inteiro, cada caso usa uma referência única e as asserções
 * olham só para ela. As datas usam o fuso do serviço (BRT), não o do sistema — o CI roda em UTC.
 *
 * <p>No perfil de teste {@code ebd.biblia.enabled=false}: nada de rede, o e-mail vai só com a
 * referência (a busca real é coberta pelos testes de normalização/parse, sem HTTP).
 */
@QuarkusTest
class LeituraDiariaTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Inject LeituraDiariaService leituraDiaria;
    @Inject BibliaOnlineService biblia;
    @Inject Fixtures fx;

    private static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    /** Dia da semana de hoje — o que o batch procura. */
    private static DiaSemanaLeitura diaDeHoje() {
        return DiaSemanaLeitura.de(hoje().getDayOfWeek());
    }

    @Test
    @TestTransaction
    void leituraDoDiaVaiParaOsAlunosComOptIn() {
        Classe c = fx.classe("Turma Leitura A");
        fx.aluno("Aluno Opt-in A", c, "leitura.a@ebd.test", true);
        fx.aluno("Aluno sem opt-in A", c, "leitura.sem@ebd.test", false);
        // Aula daqui a 3 dias: a leitura de hoje pertence à semana que a antecede.
        Aula aula = fx.aula(c, hoje().plusDays(3));
        TextoBiblicoAula leitura = fx.leitura(aula, diaDeHoje(), "Salmos 1.1-6 [teste A]");

        LeituraDiariaService.Resultado r = leituraDiaria.enviarDoDia();

        assertTrue(r.referencias().contains(leitura.getReferencia()), "a leitura de hoje deve entrar no lote");
        assertTrue(r.enviados() >= 1, "o aluno com opt-in deve receber");
        assertEquals(hoje(), leitura.getEnviadoEm(), "deve carimbar o envio do dia (dedup)");

        // 2º disparo no mesmo dia não reenvia nada desta leitura
        LeituraDiariaService.Resultado r2 = leituraDiaria.enviarDoDia();
        assertFalse(r2.referencias().contains(leitura.getReferencia()), "leitura já enviada hoje sai do lote");
    }

    @Test
    @TestTransaction
    void aulaAdiadaOuForaDaSemanaNaoEnvia() {
        // aula adiada — fora de tudo
        Classe adiada = fx.classe("Turma Leitura B");
        fx.aluno("Aluno B", adiada, "leitura.b@ebd.test", true);
        Aula aulaAdiada = fx.aula(adiada, hoje().plusDays(2));
        aulaAdiada.setAdiada(true);
        TextoBiblicoAula leituraAdiada = fx.leitura(aulaAdiada, diaDeHoje(), "João 3.16 [teste B adiada]");

        // aula fora da janela de 7 dias — a leitura ainda não é desta semana
        Classe longe = fx.classe("Turma Leitura C");
        fx.aluno("Aluno C", longe, "leitura.c@ebd.test", true);
        Aula aulaLonge = fx.aula(longe, hoje().plusDays(10));
        TextoBiblicoAula leituraLonge = fx.leitura(aulaLonge, diaDeHoje(), "Provérbios 3.5-6 [teste C longe]");

        // leitura de outro dia da semana na aula certa — não é hoje
        Classe outroDia = fx.classe("Turma Leitura D");
        fx.aluno("Aluno D", outroDia, "leitura.d@ebd.test", true);
        Aula aulaOutroDia = fx.aula(outroDia, hoje().plusDays(3));
        DiaSemanaLeitura amanha = DiaSemanaLeitura.de(hoje().plusDays(1).getDayOfWeek());
        TextoBiblicoAula leituraAmanha = fx.leitura(aulaOutroDia, amanha, "Tiago 1.5 [teste D amanhã]");

        LeituraDiariaService.Resultado r = leituraDiaria.enviarDoDia();

        assertFalse(r.referencias().contains(leituraAdiada.getReferencia()), "aula adiada não envia leitura");
        assertFalse(r.referencias().contains(leituraLonge.getReferencia()), "aula fora da semana não envia");
        assertFalse(r.referencias().contains(leituraAmanha.getReferencia()), "só o dia de hoje é enviado");
        assertNull(leituraAdiada.getEnviadoEm());
        assertNull(leituraLonge.getEnviadoEm());
    }

    @Test
    void dataDaLeituraEhODiaDaSemanaQueAntecedeAAula() {
        LocalDate domingo = LocalDate.of(2026, 8, 23); // domingo
        assertEquals(LocalDate.of(2026, 8, 17), DiaSemanaLeitura.SEGUNDA.dataAntesDe(domingo));
        assertEquals(LocalDate.of(2026, 8, 22), DiaSemanaLeitura.SABADO.dataAntesDe(domingo));
        assertEquals(LocalDate.of(2026, 8, 16), DiaSemanaLeitura.DOMINGO.dataAntesDe(domingo),
                "domingo da leitura é o domingo anterior, nunca o dia da própria aula");
    }

    @Test
    void referenciaBrasileiraViraOFormatoDaApi() {
        assertEquals("Salmos 1:1-6", BibliaOnlineService.normalizar("Sl 1.1-6"));
        assertEquals("1 João 4:7-8", BibliaOnlineService.normalizar("1Jo 4.7-8"));
        assertEquals("Provérbios 3:5-6", BibliaOnlineService.normalizar("Pv 3,5-6"));
        assertEquals("João 3:16", BibliaOnlineService.normalizar("João 3:16"));
        assertEquals("Jó 1:1", BibliaOnlineService.normalizar("Jó 1.1"), "acento distingue Jó de João");
        assertEquals("Salmos 119", BibliaOnlineService.normalizar("Salmos 119"));
    }

    @Test
    void textoDaApiViraVersiculosNumerados() {
        String json = """
                {"reference":"João 3:16-17","verses":[
                  {"verse":16,"text":"Porque Deus amou o mundo  de tal maneira,\\n que deu o seu Filho.   "},
                  {"verse":17,"text":"Porque Deus enviou o seu Filho ao mundo.   "}],
                 "translation_id":"almeida"}
                """;
        String texto = biblia.extrairTexto(json);
        assertNotNull(texto);
        assertEquals("16 Porque Deus amou o mundo de tal maneira, que deu o seu Filho.\n"
                + "17 Porque Deus enviou o seu Filho ao mundo.", texto);

        assertNull(biblia.extrairTexto("{\"error\":\"not found\"}"), "referência inválida não vira texto");
        assertNull(biblia.extrairTexto("isto não é json"));
    }
}
