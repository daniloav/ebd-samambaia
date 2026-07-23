package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.AuditoriaResponse;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.service.AuditoriaService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Consulta da auditoria de ações (só ADMIN). */
@Path("/api/auditoria")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AuditoriaResource {

    @Inject AuditoriaService service;

    @GET
    public List<AuditoriaResponse> listar(
            @QueryParam("entidade") String entidade,
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("limite") @DefaultValue("300") int limite) {
        EntidadeAuditoria ent = parseEntidade(entidade);
        LocalDateTime ini = parseData(inicio, false);
        LocalDateTime f = parseData(fim, true);
        return service.listar(ent, ini, f, limite).stream().map(AuditoriaResponse::de).toList();
    }

    private EntidadeAuditoria parseEntidade(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return EntidadeAuditoria.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Entidade inválida: " + v, Response.Status.BAD_REQUEST);
        }
    }

    private LocalDateTime parseData(String v, boolean fimDoDia) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(v.trim());
            return fimDoDia ? d.atTime(LocalTime.MAX) : d.atStartOfDay();
        } catch (Exception e) {
            throw new WebApplicationException("Data inválida: " + v, Response.Status.BAD_REQUEST);
        }
    }
}
