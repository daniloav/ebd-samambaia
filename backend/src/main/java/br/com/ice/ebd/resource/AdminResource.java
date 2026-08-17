package br.com.ice.ebd.resource;

import br.com.ice.ebd.service.AniversarioService;
import br.com.ice.ebd.service.LeituraDiariaService;
import br.com.ice.ebd.service.LembreteChamadaService;
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

    @Inject
    LembreteChamadaService lembreteChamadaService;

    @Inject
    LeituraDiariaService leituraDiariaService;

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

    /**
     * Dispara na hora o lembrete das chamadas pendentes de hoje — para testar sem esperar
     * a próxima hora cheia. Retorna quantas aulas estão sem chamada e quantos e-mails saíram.
     */
    @POST
    @Path("/lembretes-chamada/executar")
    @RolesAllowed("ADMIN")
    public LembreteChamadaService.Resultado executarLembretesChamada() {
        return lembreteChamadaService.enviarPendentes();
    }

    /**
     * Dispara na hora o envio das leituras bíblicas do dia — para testar sem esperar as 12h.
     * Retorna quantas leituras são de hoje e quantos e-mails saíram.
     */
    @POST
    @Path("/leituras-diarias/executar")
    @RolesAllowed("ADMIN")
    public LeituraDiariaService.Resultado executarLeiturasDiarias() {
        return leituraDiariaService.enviarDoDia();
    }
}
