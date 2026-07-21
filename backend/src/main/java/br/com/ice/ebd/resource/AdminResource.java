package br.com.ice.ebd.resource;

import br.com.ice.ebd.service.AniversarioService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Operações administrativas/manuais (disparos de rotina para teste). */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    AniversarioService aniversarioService;

    /**
     * Dispara na hora o envio de parabéns dos aniversariantes de hoje — para testar sem
     * esperar o agendamento das 12:00. Retorna a contagem e os nomes.
     */
    @POST
    @Path("/aniversarios/executar")
    @RolesAllowed("ADMIN")
    public AniversarioService.Resultado executarAniversarios() {
        return aniversarioService.enviarDoDia();
    }
}
