package br.com.icev.ebd.resource;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** Retorna os dados do usuário autenticado (para o front validar a sessão). */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Map<String, Object> me() {
        return Map.of(
                "username", identity.getPrincipal().getName(),
                "roles", identity.getRoles());
    }
}
