package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.DashboardResponse;
import br.com.ice.ebd.service.DashboardService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    DashboardService service;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public DashboardResponse painel(@QueryParam("classeId") Long classeId) {
        return service.gerar(classeId);
    }
}
