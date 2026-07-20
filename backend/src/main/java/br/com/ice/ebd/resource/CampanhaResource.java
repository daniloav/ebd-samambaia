package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.CampanhaRequest;
import br.com.ice.ebd.dto.CampanhaResponse;
import br.com.ice.ebd.service.CampanhaService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/campanhas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CampanhaResource {

    @Inject
    CampanhaService service;

    @Inject
    SecurityIdentity identity;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public List<CampanhaResponse> listar() {
        return service.listar();
    }

    @POST
    @RolesAllowed("ADMIN")
    public Response criar(@Valid CampanhaRequest request) {
        CampanhaResponse resp = service.criarEEnviar(request, identity.getPrincipal().getName());
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }
}
