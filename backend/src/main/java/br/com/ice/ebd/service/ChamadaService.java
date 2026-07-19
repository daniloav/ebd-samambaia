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

    @Inject AlunoRepository alunoRepository;
    @Inject PresencaRepository presencaRepository;
    @Inject AulaService aulaService;

    /**
     * Monta a chamada de uma aula: todos os alunos ativos, com os itens já
     * registrados (quando existirem) ou zerados (quando ainda não houver registro).
     */
    public ChamadaResponse obterChamada(Long aulaId) {
        Aula aula = aulaService.obter(aulaId);

        Map<Long, Presenca> porAluno = new LinkedHashMap<>();
        for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
            porAluno.put(p.getAluno().getId(), p);
        }

        List<PresencaItem> itens = alunoRepository.listarAtivos().stream()
                .map(aluno -> {
                    Presenca p = porAluno.get(aluno.getId());
                    if (p == null) {
                        return new PresencaItem(aluno.getId(), aluno.getNome(),
                                false, false, false, false);
                    }
                    return new PresencaItem(aluno.getId(), aluno.getNome(),
                            p.isPresente(), p.isTrouxeBiblia(), p.isTrouxeRevista(), p.isEstudouLicao());
                })
                .toList();

        return new ChamadaResponse(aula.getId(), aula.getData(), aula.getTema(), itens);
    }

    /** Salva (upsert) a chamada da aula, uma linha por aluno. */
    @Transactional
    public ChamadaResponse salvarChamada(Long aulaId, SalvarChamadaRequest req) {
        Aula aula = aulaService.obter(aulaId);

        Map<Long, Presenca> existentes = new LinkedHashMap<>();
        for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
            existentes.put(p.getAluno().getId(), p);
        }

        for (SalvarChamadaRequest.Item item : req.itens()) {
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
