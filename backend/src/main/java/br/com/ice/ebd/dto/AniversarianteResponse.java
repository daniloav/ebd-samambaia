package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Aluno;
import java.time.LocalDate;

/**
 * Aniversariante exibido na tela do aluno (hoje + próximos dias). Somente-leitura.
 *
 * <p>{@code whatsapp} já vem normalizado (só dígitos, com DDI 55) para o front montar o link
 * {@code wa.me}; é {@code null} quando o telefone está ausente ou é curto demais. Nenhum número
 * aparece em texto na tela — só embutido no link.</p>
 */
public record AniversarianteResponse(
        Long id,
        String nome,
        LocalDate dataNascimento,
        int dia,
        int mes,
        boolean hoje,
        String turmaNome,
        String whatsapp) {

    public static AniversarianteResponse de(Aluno a, LocalDate hoje) {
        LocalDate nasc = a.getDataNascimento();
        boolean ehHoje = nasc.getMonthValue() == hoje.getMonthValue()
                && nasc.getDayOfMonth() == hoje.getDayOfMonth();
        String turma = a.getClasse() != null ? a.getClasse().getNome() : null;
        return new AniversarianteResponse(
                a.getId(), a.getNome(), nasc,
                nasc.getDayOfMonth(), nasc.getMonthValue(), ehHoje,
                turma, normalizarWhatsapp(a.getTelefone()));
    }

    /**
     * Telefone brasileiro → dígitos com DDI 55 (ex.: {@code (61) 99999-8888} → {@code 5561999998888}).
     * Devolve {@code null} se ficar com menos de 10 dígitos (sem DDD+número válidos).
     */
    static String normalizarWhatsapp(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 10) {
            return null;
        }
        return digitos.startsWith("55") ? digitos : "55" + digitos;
    }
}
