package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.AlunoRequest;
import br.com.ice.ebd.dto.AlunoResponse;
import br.com.ice.ebd.service.AlunoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/alunos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AlunoResource {

    @Inject
    AlunoService service;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public List<AlunoResponse> listar(
            @QueryParam("classeId") Long classeId,
            @QueryParam("apenasAtivos") @DefaultValue("false") boolean apenasAtivos) {
        return service.listar(classeId, apenasAtivos);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public AlunoResponse buscar(@PathParam("id") Long id) {
        return service.buscar(id);
    }

    @POST
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response criar(@Valid AlunoRequest request) {
        AlunoResponse criado = service.criar(request);
        return Response.status(Response.Status.CREATED).entity(criado).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public AlunoResponse atualizar(@PathParam("id") Long id, @Valid AlunoRequest request) {
        return service.atualizar(id, request);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public Response deletar(@PathParam("id") Long id) {
        service.deletar(id);
        return Response.noContent().build();
    }
}
