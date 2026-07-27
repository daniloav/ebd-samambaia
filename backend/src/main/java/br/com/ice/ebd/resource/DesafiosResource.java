package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.service.DesafiosService;
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
    public DesafiosResponse rankings(@jakarta.ws.rs.QueryParam("classeId") Long classeId,
                                     @jakarta.ws.rs.QueryParam("ano") Integer ano,
                                     @jakarta.ws.rs.QueryParam("trimestre") Integer trimestre) {
        return service.gerar(classeId, ano, trimestre);
    }
}
