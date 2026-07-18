package br.com.icev.ebd.resource;

import br.com.icev.ebd.dto.DesafiosResponse;
import br.com.icev.ebd.service.DesafiosService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/desafios")
@Produces(MediaType.APPLICATION_JSON)
public class DesafiosResource {

    @Inject
    DesafiosService service;

    @GET
    @Path("/rankings")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public DesafiosResponse rankings() {
        return service.gerar();
    }
}
