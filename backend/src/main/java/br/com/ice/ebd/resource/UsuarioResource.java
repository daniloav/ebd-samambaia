package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.UsuarioRequest;
import br.com.ice.ebd.dto.UsuarioResponse;
import br.com.ice.ebd.service.UsuarioService;
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

@Path("/api/usuarios")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class UsuarioResource {

    @Inject
    UsuarioService service;

    @GET
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    @GET
    @Path("/{id}")
    public UsuarioResponse buscar(@PathParam("id") Long id) {
        return service.buscar(id);
    }

    @POST
    public Response criar(@Valid UsuarioRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.criar(request)).build();
    }

    @PUT
    @Path("/{id}")
    public UsuarioResponse atualizar(@PathParam("id") Long id, @Valid UsuarioRequest request) {
        return service.atualizar(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id) {
        service.deletar(id);
        return Response.noContent().build();
    }
}
