package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.RelatorioGeralResponse;
import br.com.ice.ebd.dto.RelatorioPresencaResponse;
import br.com.ice.ebd.dto.RelatorioVisitantesResponse;
import br.com.ice.ebd.service.RelatorioGeralService;
import br.com.ice.ebd.service.RelatorioService;
import br.com.ice.ebd.service.RelatorioVisitantesService;
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

    @Inject
    RelatorioGeralService geralService;

    @Inject
    RelatorioVisitantesService visitantesService;

    @GET
    @Path("/presencas")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public RelatorioPresencaResponse presencas(
            @QueryParam("inicio") LocalDate inicio,
            @QueryParam("fim") LocalDate fim,
            @QueryParam("classeId") Long classeId) {
        return service.gerar(inicio, fim, classeId);
    }

    /** Relatório geral consolidado de um dia (todas as turmas). Só ADMIN/superintendência. */
    @GET
    @Path("/geral")
    @RolesAllowed("ADMIN")
    public RelatorioGeralResponse geral(@QueryParam("data") LocalDate data) {
        return geralService.gerarDoDia(data);
    }

    /** Relatório de visitantes por período. classeId nulo = geral (todas as turmas, só ADMIN). */
    @GET
    @Path("/visitantes")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public RelatorioVisitantesResponse visitantes(
            @QueryParam("inicio") LocalDate inicio,
            @QueryParam("fim") LocalDate fim,
            @QueryParam("classeId") Long classeId) {
        return visitantesService.gerar(inicio, fim, classeId);
    }
}
