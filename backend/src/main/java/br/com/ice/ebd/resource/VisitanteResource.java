package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.VisitanteRequest;
import br.com.ice.ebd.dto.VisitanteResponse;
import br.com.ice.ebd.service.VisitanteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/visitantes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VisitanteResource {

    @Inject
    VisitanteService service;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public List<VisitanteResponse> listar(@QueryParam("aulaId") Long aulaId) {
        return service.listar(aulaId);
    }

    @POST
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response adicionar(@QueryParam("aulaId") Long aulaId, @Valid VisitanteRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.adicionar(aulaId, request)).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response remover(@PathParam("id") Long id) {
        service.remover(id);
        return Response.noContent().build();
    }
}
