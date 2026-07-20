package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.VisitanteRequest;
import br.com.ice.ebd.dto.VisitanteResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Visitante;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import br.com.ice.ebd.repository.VisitanteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

/**
 * Visitantes de uma aula. Ao registrar, envia boas-vindas ao visitante e avisa
 * os professores. Respeita o escopo por turma (professor só na turma dele).
 */
@ApplicationScoped
public class VisitanteService {

    @Inject VisitanteRepository repository;
    @Inject AlunoRepository alunoRepository;
    @Inject UsuarioRepository usuarioRepository;
    @Inject AulaService aulaService;
    @Inject EscopoService escopo;
    @Inject NotificacaoService notificacaoService;

    public List<VisitanteResponse> listar(Long aulaId) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());
        return repository.listarPorAula(aulaId).stream().map(VisitanteResponse::de).toList();
    }

    @Transactional
    public VisitanteResponse adicionar(Long aulaId, VisitanteRequest req) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());

        Visitante v = new Visitante();
        v.setAula(aula);
        v.setNome(req.nome().trim());
        v.setEmail(vazioNull(req.email()));
        v.setTelefone(vazioNull(req.telefone()));
        if (req.trazidoPorAlunoId() != null) {
            Aluno a = alunoRepository.findById(req.trazidoPorAlunoId());
            if (a == null) {
                throw new NotFoundException("Aluno não encontrado: " + req.trazidoPorAlunoId());
            }
            v.setTrazidoPor(a);
        }
        repository.persist(v);

        // E-mails (opt-in do servidor via toggle; falhas viram log, não quebram o cadastro).
        notificacaoService.enviarBoasVindasVisitante(v);
        notificacaoService.avisarProfessoresNovoVisitante(v, usuarioRepository.emailsDeProfessoresAtivos());

        return VisitanteResponse.de(v);
    }

    @Transactional
    public void remover(Long id) {
        Visitante v = repository.findById(id);
        if (v == null) {
            throw new NotFoundException("Visitante não encontrado: " + id);
        }
        escopo.assertClasse(v.getAula().getClasse().getId());
        repository.delete(v);
    }

    private String vazioNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
