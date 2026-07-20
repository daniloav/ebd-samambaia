package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.MinhaFrequenciaResponse;
import br.com.ice.ebd.service.MinhaFrequenciaService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** Dados do usuário autenticado e a visão própria do aluno. */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    MinhaFrequenciaService minhaFrequenciaService;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR", "ALUNO"})
    public Map<String, Object> me() {
        return Map.of(
                "username", identity.getPrincipal().getName(),
                "roles", identity.getRoles());
    }

    /** Frequência do próprio aluno logado (só ALUNO; nunca expõe outros alunos). */
    @GET
    @Path("/frequencia")
    @RolesAllowed("ALUNO")
    public MinhaFrequenciaResponse frequencia() {
        return minhaFrequenciaService.minha();
    }
}
