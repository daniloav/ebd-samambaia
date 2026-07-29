package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AniversarianteResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aniversariantes da escola para exibir na tela do aluno: quem faz aniversário de <b>hoje</b>
 * até os <b>próximos 7 dias</b>, entre todos os alunos ativos (qualquer turma). O próprio aluno
 * logado é excluído. Somente-leitura; não envia nada (o e-mail de parabéns é o batch
 * {@code AniversarioService}).
 */
@ApplicationScoped
public class AniversariantesService {

    /** Mesmo fuso do batch de aniversário ({@code AniversarioService}). */
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int JANELA_DIAS = 7;

    @Inject EscopoService escopo;
    @Inject AlunoRepository alunoRepository;

    @Transactional
    public List<AniversarianteResponse> proximos() {
        LocalDate hoje = LocalDate.now(FUSO);
        Long alunoLogadoId = escopo.alunoIdLogado();

        record Prox(Aluno aluno, long dias) {}
        List<Prox> naJanela = new ArrayList<>();
        for (Aluno a : alunoRepository.listarAtivos()) {
            if (a.getDataNascimento() == null || Objects.equals(a.getId(), alunoLogadoId)) {
                continue;
            }
            long dias = diasAteAniversario(a.getDataNascimento(), hoje);
            if (dias >= 0 && dias <= JANELA_DIAS) {
                naJanela.add(new Prox(a, dias));
            }
        }
        // Mais próximos primeiro; empate mantém a ordem alfabética (listarAtivos já vem por nome).
        naJanela.sort((x, y) -> Long.compare(x.dias(), y.dias()));

        List<AniversarianteResponse> resposta = new ArrayList<>(naJanela.size());
        for (Prox p : naJanela) {
            resposta.add(AniversarianteResponse.de(p.aluno(), hoje));
        }
        return resposta;
    }

    /** Dias de hoje até a próxima ocorrência do aniversário (0 = hoje). 29/02 cai em 28/02 em ano não-bissexto. */
    private static long diasAteAniversario(LocalDate nascimento, LocalDate hoje) {
        MonthDay md = MonthDay.of(nascimento.getMonthValue(), nascimento.getDayOfMonth());
        LocalDate proxima = md.atYear(hoje.getYear());
        if (proxima.isBefore(hoje)) {
            proxima = md.atYear(hoje.getYear() + 1);
        }
        return ChronoUnit.DAYS.between(hoje, proxima);
    }
}
