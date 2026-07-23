package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.BoletimResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o boletim de um aluno num trimestre (Jan-Mar=1º, Abr-Jun=2º, Jul-Set=3º, Out-Dez=4º):
 * notas das provas, frequência (presenças/faltas + itens) e situação.
 */
@ApplicationScoped
public class BoletimService {

    /** Limiares da situação "Aprovado". */
    static final double APROVEITAMENTO_MIN = 60.0;
    static final double FREQUENCIA_MIN = 75.0;

    @Inject EscopoService escopo;
    @Inject EntityManager em;
    @Inject AlunoRepository alunoRepository;

    /** Boletim de qualquer aluno (ADMIN/PROFESSOR), respeitando o escopo de turma. */
    public BoletimResponse gerar(Long alunoId, int ano, int trimestre) {
        Aluno aluno = obterAluno(alunoId);
        escopo.assertClasse(aluno.getClasse().getId());
        return montar(aluno, ano, trimestre);
    }

    /** Boletim do próprio aluno logado (role ALUNO). */
    public BoletimResponse gerarMeu(int ano, int trimestre) {
        Long alunoId = escopo.alunoIdLogado();
        if (alunoId == null) {
            throw new NotFoundException("Nenhum aluno vinculado ao seu usuário.");
        }
        return montar(obterAluno(alunoId), ano, trimestre);
    }

    private Aluno obterAluno(Long alunoId) {
        Aluno aluno = alunoId != null ? alunoRepository.findById(alunoId) : null;
        if (aluno == null) {
            throw new NotFoundException("Aluno não encontrado: " + alunoId);
        }
        return aluno;
    }

    private BoletimResponse montar(Aluno aluno, int ano, int trimestre) {
        if (trimestre < 1 || trimestre > 4) {
            throw new WebApplicationException("Trimestre deve ser 1, 2, 3 ou 4.", Response.Status.BAD_REQUEST);
        }
        if (ano < 2000 || ano > 2100) {
            throw new WebApplicationException("Ano inválido.", Response.Status.BAD_REQUEST);
        }
        LocalDate ini = LocalDate.of(ano, (trimestre - 1) * 3 + 1, 1);
        LocalDate fim = ini.plusMonths(3).minusDays(1);
        LocalDate hoje = LocalDate.now(); // aulas contam só até hoje (não conta as futuras do trimestre corrente)
        Long cid = aluno.getClasse().getId();
        Long aid = aluno.getId();

        // Provas do período (com a nota do aluno, se lançada).
        List<BoletimResponse.ProvaItem> provas = new ArrayList<>();
        List<BigDecimal> notas = new ArrayList<>();
        double somaAproveitamento = 0.0;
        int comNota = 0;
        for (Object[] r : em.createQuery(
                        "select pr.titulo, pr.data, pr.notaMaxima, "
                        + "(select np.nota from NotaProva np where np.prova = pr and np.aluno.id = :aid) "
                        + "from Prova pr where pr.classe.id = :cid and pr.data between :ini and :fim "
                        + "order by pr.data asc", Object[].class)
                .setParameter("aid", aid).setParameter("cid", cid)
                .setParameter("ini", ini).setParameter("fim", fim)
                .getResultList()) {
            String titulo = (String) r[0];
            LocalDate data = (LocalDate) r[1];
            BigDecimal max = (BigDecimal) r[2];
            BigDecimal nota = (BigDecimal) r[3];
            Double pct = null;
            if (nota != null && max != null && max.signum() > 0) {
                pct = nota.multiply(new BigDecimal("100")).divide(max, 1, RoundingMode.HALF_UP).doubleValue();
                somaAproveitamento += pct;
                notas.add(nota);
                comNota++;
            }
            provas.add(new BoletimResponse.ProvaItem(titulo, data, nota, max, pct));
        }
        BigDecimal mediaNotas = comNota > 0
                ? notas.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(comNota), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double aproveitamento = comNota > 0 ? Math.round(somaAproveitamento / comNota * 10.0) / 10.0 : 0.0;

        // Frequência do aluno no período.
        long totalAulas = ((Number) em.createQuery(
                        "select count(a) from Aula a where a.classe.id = :cid and a.data between :ini and :fim and a.data <= :hoje")
                .setParameter("cid", cid).setParameter("ini", ini).setParameter("fim", fim).setParameter("hoje", hoje)
                .getSingleResult()).longValue();
        Object[] ag = (Object[]) em.createQuery(
                        "select "
                        + "sum(case when p.presente = true then 1 else 0 end), "
                        + "sum(case when p.trouxeBiblia = true then 1 else 0 end), "
                        + "sum(case when p.trouxeRevista = true then 1 else 0 end), "
                        + "sum(case when p.estudouLicao = true then 1 else 0 end) "
                        + "from Presenca p where p.aluno.id = :aid and p.aula.data between :ini and :fim and p.aula.data <= :hoje")
                .setParameter("aid", aid).setParameter("ini", ini).setParameter("fim", fim).setParameter("hoje", hoje)
                .getSingleResult();
        long presencas = toLong(ag[0]);
        long faltas = Math.max(0, totalAulas - presencas);
        double percPresenca = totalAulas > 0
                ? Math.round(presencas * 10000.0 / totalAulas) / 100.0 : 0.0;
        BoletimResponse.Frequencia freq = new BoletimResponse.Frequencia(
                totalAulas, presencas, faltas, percPresenca,
                toLong(ag[1]), toLong(ag[2]), toLong(ag[3]));

        long visitantes = ((Number) em.createQuery(
                        "select count(v) from Visitante v where v.trazidoPor.id = :aid "
                        + "and v.aula.data between :ini and :fim and v.aula.data <= :hoje")
                .setParameter("aid", aid).setParameter("ini", ini).setParameter("fim", fim).setParameter("hoje", hoje)
                .getSingleResult()).longValue();

        boolean trimestreEncerrado = hoje.isAfter(fim); // enquanto não fecha, não dá veredito
        String situacao = situacao(totalAulas, comNota, percPresenca, aproveitamento, trimestreEncerrado);

        return new BoletimResponse(aid, aluno.getNome(), aluno.getClasse().getNome(),
                ano, trimestre, ini, fim, provas, mediaNotas, aproveitamento, freq, visitantes, situacao);
    }

    private String situacao(long totalAulas, int comNota, double percPresenca, double aproveitamento,
                            boolean trimestreEncerrado) {
        if (totalAulas == 0 && comNota == 0) {
            return "Sem registros no período";
        }
        // Boletim é por trimestre: enquanto o trimestre não encerra, não há veredito (pode mudar
        // com aulas/provas futuras). Só ao fechar calcula Aprovado/Em recuperação.
        if (!trimestreEncerrado) {
            return "Trimestre em andamento";
        }
        boolean freqOk = percPresenca >= FREQUENCIA_MIN;
        boolean notaOk = comNota == 0 || aproveitamento >= APROVEITAMENTO_MIN;
        return (freqOk && notaOk) ? "Aprovado" : "Em recuperação";
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
