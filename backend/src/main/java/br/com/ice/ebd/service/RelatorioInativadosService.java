package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.RelatorioInativadosResponse;
import br.com.ice.ebd.model.AlunoInativacao;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.MotivoInativacao;
import br.com.ice.ebd.repository.ClasseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relatório de alunos inativados num período — geral (todas as turmas, só ADMIN) ou por turma.
 * Lê o histórico ({@code aluno_inativacao}), então mostra também quem já voltou, e traz a
 * última presença de cada um para o trabalho pastoral de busca.
 *
 * <p>Sem período informado ({@code inicio} e {@code fim} nulos) entram também os episódios
 * <b>sem data</b> — o histórico importado pela V30, anterior ao registro de inativações.</p>
 */
@ApplicationScoped
public class RelatorioInativadosService {

    @Inject EscopoService escopo;
    @Inject EntityManager em;
    @Inject ClasseRepository classeRepository;

    public RelatorioInativadosResponse gerar(LocalDate inicio, LocalDate fim, Long classeId,
                                             boolean incluirReativados) {
        escopo.assertClasse(classeId);
        boolean periodoAberto = inicio == null && fim == null;
        LocalDate ini = inicio != null ? inicio : LocalDate.of(2000, 1, 1);
        LocalDate f = fim != null ? fim : LocalDate.now();
        long cid = classeId != null ? classeId : -1L;

        StringBuilder jpql = new StringBuilder(
                "select i from AlunoInativacao i join fetch i.aluno a join fetch a.classe "
                + "where (:cid = -1 or a.classe.id = :cid) ");
        if (!incluirReativados) {
            jpql.append("and i.reativadoEm is null ");
        }
        jpql.append(periodoAberto
                ? "and (i.inativadoEm is null or i.inativadoEm between :ini and :fim) "
                : "and i.inativadoEm between :ini and :fim ");
        jpql.append("order by i.inativadoEm desc nulls last, a.nome asc");

        List<AlunoInativacao> episodios = em.createQuery(jpql.toString(), AlunoInativacao.class)
                .setParameter("cid", cid)
                .setParameter("ini", ini.atStartOfDay())
                .setParameter("fim", f.atTime(23, 59, 59))
                .getResultList();

        Map<Long, LocalDate> ultimaPresenca = ultimaPresencaPorAluno(
                episodios.stream().map(i -> i.getAluno().getId()).distinct().toList());

        List<RelatorioInativadosResponse.Item> itens = episodios.stream()
                .map(i -> new RelatorioInativadosResponse.Item(
                        i.getAluno().getId(), i.getAluno().getNome(), i.getAluno().getClasse().getNome(),
                        i.getAluno().getEmail(), i.getAluno().getTelefone(),
                        i.getInativadoEm(), i.getMotivo(), i.getFaltasSeguidas(), i.getInativadoPor(),
                        ultimaPresenca.get(i.getAluno().getId()), i.getReativadoEm()))
                .toList();

        int reativados = (int) itens.stream().filter(RelatorioInativadosResponse.Item::reativado).count();
        int porFaltas = (int) itens.stream().filter(i -> i.motivo() == MotivoInativacao.FALTAS_SEGUIDAS).count();
        int manuais = (int) itens.stream().filter(i -> i.motivo() == MotivoInativacao.MANUAL).count();

        String classeNome = null;
        if (classeId != null) {
            Classe c = classeRepository.findById(classeId);
            classeNome = c != null ? c.getNome() : null;
        }
        return new RelatorioInativadosResponse(ini, f, periodoAberto, classeId, classeNome,
                itens.size(), itens.size() - reativados, reativados, porFaltas, manuais,
                contarSemData(cid, incluirReativados), itens);
    }

    /** Data da última aula (não adiada) em que cada aluno esteve presente. */
    private Map<Long, LocalDate> ultimaPresencaPorAluno(List<Long> alunoIds) {
        Map<Long, LocalDate> ultima = new HashMap<>();
        if (alunoIds.isEmpty()) {
            return ultima;
        }
        for (Object[] l : em.createQuery(
                        "select p.aluno.id, max(p.aula.data) from Presenca p "
                        + "where p.presente = true and p.aula.adiada = false and p.aluno.id in :ids "
                        + "group by p.aluno.id", Object[].class)
                .setParameter("ids", alunoIds)
                .getResultList()) {
            ultima.put((Long) l[0], (LocalDate) l[1]);
        }
        return ultima;
    }

    /** Episódios do escopo sem data de inativação — ficam de fora quando há filtro de período. */
    private long contarSemData(long cid, boolean incluirReativados) {
        String jpql = "select count(i) from AlunoInativacao i where i.inativadoEm is null "
                + "and (:cid = -1 or i.aluno.classe.id = :cid)"
                + (incluirReativados ? "" : " and i.reativadoEm is null");
        return em.createQuery(jpql, Long.class).setParameter("cid", cid).getSingleResult();
    }
}
