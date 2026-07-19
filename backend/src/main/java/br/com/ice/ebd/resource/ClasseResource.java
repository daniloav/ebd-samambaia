package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.ClasseRequest;
import br.com.ice.ebd.dto.ClasseResponse;
import br.com.ice.ebd.service.ClasseService;
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

@Path("/api/classes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClasseResource {

    @Inject
    ClasseService service;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR", "ALUNO"})
    public List<ClasseResponse> listar(@QueryParam("apenasAtivas") @DefaultValue("false") boolean apenasAtivas) {
        return service.listar(apenasAtivas);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR", "ALUNO"})
    public ClasseResponse buscar(@PathParam("id") Long id) {
        return service.buscar(id);
    }

    @POST
    @RolesAllowed("ADMIN")
    public Response criar(@Valid ClasseRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.criar(request)).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public ClasseResponse atualizar(@PathParam("id") Long id, @Valid ClasseRequest request) {
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
