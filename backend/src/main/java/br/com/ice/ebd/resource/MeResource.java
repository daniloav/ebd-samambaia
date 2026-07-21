package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.BoletimResponse;
import br.com.ice.ebd.dto.MinhaFrequenciaResponse;
import br.com.ice.ebd.dto.TrocarSenhaRequest;
import br.com.ice.ebd.service.BoletimService;
import br.com.ice.ebd.service.MinhaFrequenciaService;
import br.com.ice.ebd.service.UsuarioService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** Dados do usuário autenticado e a visão própria do aluno. */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    MinhaFrequenciaService minhaFrequenciaService;

    @Inject
    UsuarioService usuarioService;

    @Inject
    BoletimService boletimService;

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

    /** Troca da própria senha (qualquer usuário autenticado, para a sua conta). */
    @PUT
    @Path("/senha")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "PROFESSOR", "ALUNO"})
    public Response trocarSenha(TrocarSenhaRequest req) {
        usuarioService.trocarPropriaSenha(identity.getPrincipal().getName(), req);
        return Response.noContent().build();
    }

    /** Boletim do próprio aluno num trimestre (só ALUNO; nunca expõe outros alunos). */
    @GET
    @Path("/boletim")
    @RolesAllowed("ALUNO")
    public BoletimResponse boletim(@QueryParam("ano") int ano, @QueryParam("trimestre") int trimestre) {
        return boletimService.gerarMeu(ano, trimestre);
    }
}
