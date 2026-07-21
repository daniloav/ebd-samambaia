package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.CampanhaRequest;
import br.com.ice.ebd.dto.CampanhaResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Campanha;
import br.com.ice.ebd.model.CampanhaImagem;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.CampanhaImagemRepository;
import br.com.ice.ebd.repository.CampanhaRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Campanhas: e-mail em massa aos alunos com opt-in, com imagens/artes opcionais
 * embutidas inline no e-mail. Registra a campanha como histórico.
 */
@ApplicationScoped
public class CampanhaService {

    /** Limites de anexo (ver também quarkus.http.limits.max-body-size). */
    public static final int MAX_IMAGENS = 5;
    public static final long MAX_BYTES = 2L * 1024 * 1024; // 2 MB por imagem
    public static final Set<String> TIPOS_OK =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /** Imagem recebida no upload (antes de virar entidade). */
    public record ImagemUpload(String nome, String tipo, byte[] bytes) {}

    @Inject CampanhaRepository repository;
    @Inject CampanhaImagemRepository imagemRepository;
    @Inject ClasseRepository classeRepository;
    @Inject AlunoRepository alunoRepository;
    @Inject NotificacaoService notificacaoService;

    public List<CampanhaResponse> listar() {
        return repository.listarRecentes().stream()
                .map(c -> CampanhaResponse.de(c, metasDaCampanha(c.getId())))
                .toList();
    }

    private List<CampanhaResponse.ImagemMeta> metasDaCampanha(Long campanhaId) {
        return imagemRepository.metadataPorCampanha(campanhaId).stream()
                .map(r -> new CampanhaResponse.ImagemMeta((Long) r[0], (String) r[1], (String) r[2]))
                .toList();
    }

    @Transactional
    public CampanhaResponse criarEEnviar(CampanhaRequest req, List<ImagemUpload> imagens, String username) {
        if (!notificacaoService.isNotificacaoHabilitada()) {
            throw new WebApplicationException(
                    "O envio de e-mail está desabilitado no servidor. "
                            + "Configure o SMTP e ligue ebd.notificacoes.enabled.",
                    Response.Status.CONFLICT);
        }
        validarTexto(req);
        validarImagens(imagens);

        Classe classe = null;
        if (req.classeId() != null) {
            classe = classeRepository.findById(req.classeId());
            if (classe == null) {
                throw new NotFoundException("Classe não encontrada: " + req.classeId());
            }
        }
        String turmaLabel = classe != null ? classe.getNome() : "Todas as turmas";

        Campanha c = new Campanha();
        c.setTitulo(req.titulo().trim());
        c.setMensagem(req.mensagem());
        c.setClasse(classe);
        c.setCriadoPor(username);
        repository.persist(c);

        List<CampanhaImagem> salvas = new ArrayList<>();
        int ordem = 0;
        for (ImagemUpload up : imagens) {
            CampanhaImagem img = new CampanhaImagem();
            img.setCampanha(c);
            img.setNome(up.nome());
            img.setTipo(up.tipo());
            img.setConteudo(up.bytes());
            img.setOrdem(ordem++);
            imagemRepository.persist(img);
            salvas.add(img);
        }

        List<Aluno> destinatarios = alunoRepository.listarDestinatariosEmail(req.classeId());
        int enviados = notificacaoService.enviarCampanha(
                req.titulo().trim(), req.mensagem(), destinatarios, turmaLabel, salvas);
        c.setTotalEnviados(enviados);

        return CampanhaResponse.de(c, salvas.stream().map(CampanhaResponse.ImagemMeta::de).toList());
    }

    /** Serve o conteúdo binário de uma imagem (usado no preview do histórico). */
    public CampanhaImagem obterImagem(Long id) {
        CampanhaImagem img = imagemRepository.findById(id);
        if (img == null) {
            throw new NotFoundException("Imagem não encontrada: " + id);
        }
        return img;
    }

    private void validarTexto(CampanhaRequest req) {
        if (req.titulo() == null || req.titulo().isBlank()) {
            throw new WebApplicationException("O título é obrigatório.", Response.Status.BAD_REQUEST);
        }
        if (req.titulo().length() > 150) {
            throw new WebApplicationException("O título deve ter no máximo 150 caracteres.", Response.Status.BAD_REQUEST);
        }
        if (req.mensagem() == null || req.mensagem().isBlank()) {
            throw new WebApplicationException("A mensagem é obrigatória.", Response.Status.BAD_REQUEST);
        }
        if (req.mensagem().length() > 5000) {
            throw new WebApplicationException("A mensagem deve ter no máximo 5000 caracteres.", Response.Status.BAD_REQUEST);
        }
    }

    private void validarImagens(List<ImagemUpload> imagens) {
        if (imagens == null || imagens.isEmpty()) {
            return;
        }
        if (imagens.size() > MAX_IMAGENS) {
            throw new WebApplicationException(
                    "Máximo de " + MAX_IMAGENS + " imagens por campanha.", Response.Status.BAD_REQUEST);
        }
        for (ImagemUpload up : imagens) {
            if (up.bytes() == null || up.bytes().length == 0) {
                throw new WebApplicationException("Imagem vazia: " + up.nome(), Response.Status.BAD_REQUEST);
            }
            if (up.bytes().length > MAX_BYTES) {
                throw new WebApplicationException(
                        "A imagem \"" + up.nome() + "\" excede 2 MB.", Response.Status.BAD_REQUEST);
            }
            String tipo = up.tipo() != null ? up.tipo().toLowerCase() : "";
            if (!TIPOS_OK.contains(tipo)) {
                throw new WebApplicationException(
                        "Tipo de imagem não suportado (" + up.nome() + "): use JPG, PNG, GIF ou WEBP.",
                        Response.Status.BAD_REQUEST);
            }
        }
    }
}
