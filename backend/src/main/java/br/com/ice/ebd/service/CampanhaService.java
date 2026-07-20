package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.CampanhaRequest;
import br.com.ice.ebd.dto.CampanhaResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Campanha;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.CampanhaRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Campanhas: envia um e-mail em massa aos alunos com opt-in (todos ou de uma turma)
 * e registra a campanha como histórico. Reaproveita o {@link NotificacaoService}.
 */
@ApplicationScoped
public class CampanhaService {

    @Inject CampanhaRepository repository;
    @Inject ClasseRepository classeRepository;
    @Inject AlunoRepository alunoRepository;
    @Inject NotificacaoService notificacaoService;

    public List<CampanhaResponse> listar() {
        return repository.listarRecentes().stream().map(CampanhaResponse::de).toList();
    }

    /** Cria a campanha, dispara os e-mails e persiste com a contagem de enviados. */
    @Transactional
    public CampanhaResponse criarEEnviar(CampanhaRequest req, String username) {
        if (!notificacaoService.isNotificacaoHabilitada()) {
            throw new WebApplicationException(
                    "O envio de e-mail está desabilitado no servidor. "
                            + "Configure o SMTP e ligue ebd.notificacoes.enabled.",
                    Response.Status.CONFLICT);
        }

        Classe classe = null;
        if (req.classeId() != null) {
            classe = classeRepository.findById(req.classeId());
            if (classe == null) {
                throw new NotFoundException("Classe não encontrada: " + req.classeId());
            }
        }
        String turmaLabel = classe != null ? classe.getNome() : "Todas as turmas";

        List<Aluno> destinatarios = alunoRepository.listarDestinatariosEmail(req.classeId());
        int enviados = notificacaoService.enviarCampanha(
                req.titulo().trim(), req.mensagem(), destinatarios, turmaLabel);

        Campanha c = new Campanha();
        c.setTitulo(req.titulo().trim());
        c.setMensagem(req.mensagem());
        c.setClasse(classe);
        c.setTotalEnviados(enviados);
        c.setCriadoPor(username);
        repository.persist(c);
        return CampanhaResponse.de(c);
    }
}
