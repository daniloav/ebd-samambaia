package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.RelatorioPresencaResponse;
import br.com.ice.ebd.service.RelatorioService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;

@Path("/api/relatorios")
@Produces(MediaType.APPLICATION_JSON)
public class RelatorioResource {

    @Inject
    RelatorioService service;

    @GET
    @Path("/presencas")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public RelatorioPresencaResponse presencas(
            @QueryParam("inicio") LocalDate inicio,
            @QueryParam("fim") LocalDate fim) {
        return service.gerar(inicio, fim);
    }
}
