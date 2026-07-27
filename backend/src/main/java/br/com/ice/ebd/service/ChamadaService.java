package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.ChamadaResponse;
import br.com.ice.ebd.dto.PresencaItem;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ChamadaService {

    @Inject EscopoService escopo;

    @Inject AlunoRepository alunoRepository;
    @Inject PresencaRepository presencaRepository;
    @Inject AulaService aulaService;

    /**
     * Monta a chamada de uma aula: todos os alunos ativos, com os itens já
     * registrados (quando existirem) ou zerados (quando ainda não houver registro).
     */
    @Transactional
    public ChamadaResponse obterChamada(Long aulaId) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());

        Long professorAlunoId = professorAlunoId(aula);

        Map<Long, Presenca> porAluno = new LinkedHashMap<>();
        for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
            porAluno.put(p.getAluno().getId(), p);
        }

        List<PresencaItem> itens = alunoRepository.listarAtivosPorClasse(aula.getClasse().getId()).stream()
                .map(aluno -> {
                    boolean ehProfessor = aluno.getId().equals(professorAlunoId);
                    Presenca p = porAluno.get(aluno.getId());
                    if (p == null || ehProfessor) {
                        return new PresencaItem(aluno.getId(), aluno.getNome(),
                                false, false, false, false, ehProfessor);
                    }
                    return new PresencaItem(aluno.getId(), aluno.getNome(),
                            p.isPresente(), p.isTrouxeBiblia(), p.isTrouxeRevista(), p.isEstudouLicao(), false);
                })
                .toList();

        return new ChamadaResponse(aula.getId(), aula.getData(), aula.getTema(), itens);
    }

    /** Id do aluno vinculado ao professor da aula (ou null). Esse aluno não conta na chamada/ranking. */
    private static Long professorAlunoId(Aula aula) {
        return aula.getProfessor() != null && aula.getProfessor().getAluno() != null
                ? aula.getProfessor().getAluno().getId() : null;
    }

    /** Salva (upsert) a chamada da aula, uma linha por aluno. */
    @Transactional
    public ChamadaResponse salvarChamada(Long aulaId, SalvarChamadaRequest req) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());

        Long professorAlunoId = professorAlunoId(aula);
        if (professorAlunoId != null) {
            // o professor da aula não é contabilizado: remove qualquer presença dele nesta aula
            presencaRepository.delete("aula.id = ?1 and aluno.id = ?2", aulaId, professorAlunoId);
        }

        Map<Long, Presenca> existentes = new LinkedHashMap<>();
        for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
            existentes.put(p.getAluno().getId(), p);
        }

        for (SalvarChamadaRequest.Item item : req.itens()) {
            if (professorAlunoId != null && professorAlunoId.equals(item.alunoId())) {
                continue; // professor da aula — não registra presença
            }
            Aluno aluno = alunoRepository.findById(item.alunoId());
            if (aluno == null) {
                throw new NotFoundException("Aluno não encontrado: " + item.alunoId());
            }
            Presenca p = existentes.get(item.alunoId());
            if (p == null) {
                p = new Presenca();
                p.setAula(aula);
                p.setAluno(aluno);
            }
            p.setPresente(item.presente());
            p.setTrouxeBiblia(item.trouxeBiblia());
            p.setTrouxeRevista(item.trouxeRevista());
            p.setEstudouLicao(item.estudouLicao());
            presencaRepository.persist(p);
        }

        return obterChamada(aulaId);
    }
}
