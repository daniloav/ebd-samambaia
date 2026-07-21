package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.RelatorioVisitantesResponse;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Visitante;
import br.com.ice.ebd.repository.ClasseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório de visitantes por período — geral (todas as turmas, só ADMIN) ou por turma.
 * O escopo é garantido por {@link EscopoService#assertClasse(Long)}: com {@code classeId}
 * nulo apenas ADMIN passa; para PROFESSOR a turma precisa estar no escopo dele.
 */
@ApplicationScoped
public class RelatorioVisitantesService {

    @Inject EscopoService escopo;
    @Inject EntityManager em;
    @Inject ClasseRepository classeRepository;

    public RelatorioVisitantesResponse gerar(LocalDate inicio, LocalDate fim, Long classeId) {
        escopo.assertClasse(classeId);
        LocalDate ini = inicio != null ? inicio : LocalDate.of(2000, 1, 1);
        LocalDate f = fim != null ? fim : LocalDate.now();
        long cid = classeId != null ? classeId : -1L;

        List<Visitante> visitantes = em.createQuery(
                        "select v from Visitante v "
                        + "join fetch v.aula a join fetch a.classe "
                        + "left join fetch v.trazidoPor "
                        + "where a.data between :ini and :fim and (:cid = -1 or a.classe.id = :cid) "
                        + "order by a.data desc, v.nome asc", Visitante.class)
                .setParameter("ini", ini).setParameter("fim", f).setParameter("cid", cid)
                .getResultList();

        List<RelatorioVisitantesResponse.Item> itens = visitantes.stream()
                .map(v -> new RelatorioVisitantesResponse.Item(
                        v.getId(), v.getNome(), v.getEmail(), v.getTelefone(),
                        v.getAula().getClasse().getNome(), v.getAula().getData(),
                        v.getTrazidoPor() != null ? v.getTrazidoPor().getNome() : null))
                .toList();

        String classeNome = null;
        if (classeId != null) {
            Classe c = classeRepository.findById(classeId);
            classeNome = c != null ? c.getNome() : null;
        }
        return new RelatorioVisitantesResponse(ini, f, classeId, classeNome, itens.size(), itens);
    }
}
