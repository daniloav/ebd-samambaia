package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.ChamadaResponse;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.service.ChamadaService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/aulas/{aulaId}/chamada")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChamadaResource {

    @Inject
    ChamadaService service;

    @Inject
    br.com.ice.ebd.service.NotificacaoService notificacaoService;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public ChamadaResponse obter(@PathParam("aulaId") Long aulaId) {
        return service.obterChamada(aulaId);
    }

    @PUT
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public ChamadaResponse salvar(@PathParam("aulaId") Long aulaId, @Valid SalvarChamadaRequest request) {
        ChamadaResponse resp = service.salvarChamada(aulaId, request);
        int emailsEnviados = notificacaoService.notificarChamada(aulaId);
        return resp.comEmailsEnviados(emailsEnviados);
    }
}
