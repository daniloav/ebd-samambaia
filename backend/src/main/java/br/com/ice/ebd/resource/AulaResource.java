package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.AulaAdiarResponse;
import br.com.ice.ebd.dto.AulaComplementarRequest;
import br.com.ice.ebd.dto.AulaComplementarResponse;
import br.com.ice.ebd.dto.AulaRequest;
import br.com.ice.ebd.dto.AulaResponse;
import br.com.ice.ebd.service.AulaService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/aulas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AulaResource {

    @Inject
    AulaService service;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public List<AulaResponse> listar(@jakarta.ws.rs.QueryParam("classeId") Long classeId) {
        return service.listar(classeId);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public AulaResponse buscar(@PathParam("id") Long id) {
        return service.buscar(id);
    }

    @POST
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response criar(@Valid AulaRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.criar(request)).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public AulaResponse atualizar(@PathParam("id") Long id, @Valid AulaRequest request) {
        return service.atualizar(id, request);
    }

    /**
     * Desdobra a aula: cria a continuação no próximo domingo e empurra +7 dias a agenda seguinte.
     */
    @POST
    @Path("/{id}/complementar")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response complementar(@PathParam("id") Long id, @Valid AulaComplementarRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.complementar(id, request)).build();
    }

    /**
     * Adia (cancela) a aula: desabilita a pontuação/retrospecto dela, empurra +7 dias a agenda
     * seguinte da turma e cria a aula de reposição no domingo liberado.
     */
    @POST
    @Path("/{id}/adiar")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public AulaAdiarResponse adiar(@PathParam("id") Long id) {
        return service.adiar(id);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public Response deletar(@PathParam("id") Long id) {
        service.deletar(id);
        return Response.noContent().build();
    }
}
