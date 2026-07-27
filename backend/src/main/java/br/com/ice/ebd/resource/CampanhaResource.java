package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.CampanhaRequest;
import br.com.ice.ebd.dto.CampanhaResponse;
import br.com.ice.ebd.model.CampanhaImagem;
import br.com.ice.ebd.service.CampanhaService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/campanhas")
public class CampanhaResource {

    @Inject
    CampanhaService service;

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public List<CampanhaResponse> listar() {
        return service.listar();
    }

    /** Cria e envia a campanha. Multipart: campos de texto + 0..N imagens (arte). */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response criar(
            @RestForm String titulo,
            @RestForm String mensagem,
            @RestForm String classeId,
            @RestForm("imagens") List<FileUpload> imagens) throws IOException {

        Long cid = null;
        if (classeId != null && !classeId.isBlank() && !"null".equalsIgnoreCase(classeId)) {
            try {
                cid = Long.valueOf(classeId.trim());
            } catch (NumberFormatException e) {
                throw new WebApplicationException("classeId inválido.", Response.Status.BAD_REQUEST);
            }
        }
        CampanhaRequest req = new CampanhaRequest(titulo, mensagem, cid);

        List<CampanhaService.ImagemUpload> ups = new ArrayList<>();
        if (imagens != null) {
            for (FileUpload fu : imagens) {
                if (fu == null || fu.fileName() == null) {
                    continue;
                }
                ups.add(new CampanhaService.ImagemUpload(
                        fu.fileName(), fu.contentType(), Files.readAllBytes(fu.uploadedFile())));
            }
        }

        CampanhaResponse resp = service.criarEEnviar(req, ups, identity.getPrincipal().getName());
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    /** Conteúdo binário de uma imagem da campanha (para o preview no histórico). */
    @GET
    @Path("/imagens/{id}")
    @Produces(MediaType.WILDCARD)
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response imagem(@PathParam("id") Long id) {
        CampanhaImagem img = service.obterImagem(id);
        return Response.ok(img.getConteudo()).type(img.getTipo()).build();
    }
}
