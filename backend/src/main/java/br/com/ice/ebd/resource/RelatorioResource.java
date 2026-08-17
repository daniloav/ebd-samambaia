package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.RelatorioGeralResponse;
import br.com.ice.ebd.dto.RelatorioInativadosResponse;
import br.com.ice.ebd.dto.RelatorioMensalResponse;
import br.com.ice.ebd.dto.RelatorioPresencaResponse;
import br.com.ice.ebd.dto.RelatorioVisitantesResponse;
import br.com.ice.ebd.service.RelatorioGeralService;
import br.com.ice.ebd.service.RelatorioInativadosService;
import br.com.ice.ebd.service.RelatorioMensalService;
import br.com.ice.ebd.service.RelatorioService;
import br.com.ice.ebd.service.RelatorioVisitantesService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.List;

@Path("/api/relatorios")
@Produces(MediaType.APPLICATION_JSON)
public class RelatorioResource {

    @Inject
    RelatorioService service;

    @Inject
    RelatorioGeralService geralService;

    @Inject
    RelatorioVisitantesService visitantesService;

    @Inject
    RelatorioInativadosService inativadosService;

    @Inject
    RelatorioMensalService mensalService;

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

    /**
     * Relatório de alunos inativados. Sem início/fim entram todos os episódios, inclusive os
     * sem data (histórico anterior ao registro). {@code incluirReativados} traz também quem voltou.
     */
    @GET
    @Path("/inativados")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public RelatorioInativadosResponse inativados(
            @QueryParam("inicio") LocalDate inicio,
            @QueryParam("fim") LocalDate fim,
            @QueryParam("classeId") Long classeId,
            @QueryParam("incluirReativados") @DefaultValue("false") boolean incluirReativados) {
        return inativadosService.gerar(inicio, fim, classeId, incluirReativados);
    }

    /**
     * Relatório geral de presença de um mês (ou do ano inteiro, com {@code mes} vazio),
     * consolidando as turmas escolhidas. {@code classeIds} aceita repetição
     * (<code>?classeIds=1&classeIds=2</code>); vazio = todas as turmas que o usuário pode ver.
     */
    @GET
    @Path("/mensal")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public RelatorioMensalResponse mensal(
            @QueryParam("ano") Integer ano,
            @QueryParam("mes") Integer mes,
            @QueryParam("classeIds") List<Long> classeIds) {
        return mensalService.gerar(ano, mes, classeIds);
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
