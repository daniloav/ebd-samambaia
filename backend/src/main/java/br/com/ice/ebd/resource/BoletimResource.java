package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.BoletimResponse;
import br.com.ice.ebd.service.BoletimService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Boletim por aluno/trimestre para ADMIN e PROFESSOR (o aluno usa /api/me/boletim). */
@Path("/api/boletim")
@Produces(MediaType.APPLICATION_JSON)
public class BoletimResource {

    @Inject
    BoletimService service;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public BoletimResponse gerar(
            @QueryParam("alunoId") Long alunoId,
            @QueryParam("ano") int ano,
            @QueryParam("trimestre") int trimestre) {
        return service.gerar(alunoId, ano, trimestre);
    }
}
