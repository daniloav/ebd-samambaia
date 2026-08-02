package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.UsoResponse;
import br.com.ice.ebd.service.UsoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Painel de estatísticas de uso (engajamento). Restrito ao ADMIN. */
@Path("/api/uso")
@Produces(MediaType.APPLICATION_JSON)
public class UsoResource {

    @Inject
    UsoService service;

    @GET
    @RolesAllowed("ADMIN")
    public UsoResponse painel() {
        return service.gerar();
    }
}
