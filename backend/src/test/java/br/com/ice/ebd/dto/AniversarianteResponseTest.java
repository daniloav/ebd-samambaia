package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes puros (sem contexto Quarkus) do mapeamento e da normalização do telefone. */
class AniversarianteResponseTest {

    @Test
    void normalizaTelefoneBrasileiroComDdi55() {
        assertEquals("5561999998888", AniversarianteResponse.normalizarWhatsapp("(61) 99999-8888"));
        assertEquals("5561999998888", AniversarianteResponse.normalizarWhatsapp("61 99999-8888"));
    }

    @Test
    void mantemDdi55QuandoJaPresente() {
        assertEquals("5561999998888", AniversarianteResponse.normalizarWhatsapp("+55 (61) 99999-8888"));
    }

    @Test
    void telefoneAusenteOuCurtoViraNull() {
        assertNull(AniversarianteResponse.normalizarWhatsapp(null));
        assertNull(AniversarianteResponse.normalizarWhatsapp(""));
        assertNull(AniversarianteResponse.normalizarWhatsapp("1234"));
    }

    @Test
    void deMarcaHojeEExtraiDiaMes() {
        LocalDate hoje = LocalDate.of(2026, 7, 29);
        Classe c = new Classe();
        c.setNome("Adultos");
        Aluno a = new Aluno();
        a.setId(1L);
        a.setNome("João da Silva");
        a.setTelefone("(61) 99999-8888");
        a.setDataNascimento(LocalDate.of(1990, 7, 29));
        a.setClasse(c);

        AniversarianteResponse r = AniversarianteResponse.de(a, hoje);
        assertEquals(29, r.dia());
        assertEquals(7, r.mes());
        assertTrue(r.hoje());
        assertEquals("Adultos", r.turmaNome());
        assertEquals("5561999998888", r.whatsapp());
    }

    @Test
    void deNaoMarcaHojeEmOutraData() {
        LocalDate hoje = LocalDate.of(2026, 7, 29);
        Aluno a = new Aluno();
        a.setId(2L);
        a.setNome("Maria");
        a.setDataNascimento(LocalDate.of(1985, 8, 3));

        AniversarianteResponse r = AniversarianteResponse.de(a, hoje);
        assertFalse(r.hoje());
        assertNull(r.whatsapp());
    }
}
