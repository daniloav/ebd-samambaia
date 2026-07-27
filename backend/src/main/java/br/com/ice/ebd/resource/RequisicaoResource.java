package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.AvaliarRequest;
import br.com.ice.ebd.model.CategoriaAnexo;
import br.com.ice.ebd.dto.RequisicaoRequest;
import br.com.ice.ebd.dto.RequisicaoResponse;
import br.com.ice.ebd.model.RequisicaoAnexo;
import br.com.ice.ebd.service.CobrancaNotaService;
import br.com.ice.ebd.service.RequisicaoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** Requisições da tesouraria. Acesso restrito a LIDER/TESOUREIRO/ADMIN. */
@Path("/api/requisicoes")
@Produces(MediaType.APPLICATION_JSON)
public class RequisicaoResource {

    @Inject RequisicaoService service;
    @Inject CobrancaNotaService cobrancaService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"LIDER", "ADMIN"})
    public Response criar(@Valid RequisicaoRequest req) {
        return Response.status(Response.Status.CREATED).entity(service.criar(req)).build();
    }

    @GET
    @RolesAllowed({"LIDER", "TESOUREIRO", "ADMIN"})
    public List<RequisicaoResponse> listar(@QueryParam("status") String status) {
        return service.listar(status);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"LIDER", "TESOUREIRO", "ADMIN"})
    public RequisicaoResponse buscar(@PathParam("id") Long id) {
        return service.buscar(id);
    }

    @POST
    @Path("/{id}/aprovar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"TESOUREIRO", "ADMIN"})
    public RequisicaoResponse aprovar(
            @PathParam("id") Long id,
            @RestForm String valorAprovado,
            @RestForm String parecer,
            @RestForm("comprovante") FileUpload comprovante) throws IOException {
        BigDecimal valor = valorAprovado != null && !valorAprovado.isBlank() && !"null".equalsIgnoreCase(valorAprovado)
                ? new BigDecimal(valorAprovado.trim().replace(",", ".")) : null;
        RequisicaoService.AnexoData anexo = null;
        if (comprovante != null && comprovante.fileName() != null) {
            anexo = new RequisicaoService.AnexoData(comprovante.fileName(), comprovante.contentType(),
                    Files.readAllBytes(comprovante.uploadedFile()), CategoriaAnexo.COMPROVANTE);
        }
        return service.aprovar(id, valor, parecer, anexo);
    }

    @POST
    @Path("/{id}/negar")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"TESOUREIRO", "ADMIN"})
    public RequisicaoResponse negar(@PathParam("id") Long id, AvaliarRequest req) {
        return service.negar(id, req);
    }

    @POST
    @Path("/{id}/finalizar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"LIDER", "ADMIN"})
    public RequisicaoResponse finalizar(
            @PathParam("id") Long id,
            @RestForm String valorGasto,
            @RestForm String observacao,
            @RestForm("anexos") List<FileUpload> anexos) throws IOException {
        BigDecimal gasto = valorGasto != null && !valorGasto.isBlank() && !"null".equalsIgnoreCase(valorGasto)
                ? new BigDecimal(valorGasto.trim().replace(",", ".")) : null;
        List<RequisicaoService.AnexoData> arquivos = new ArrayList<>();
        if (anexos != null) {
            for (FileUpload fu : anexos) {
                if (fu == null || fu.fileName() == null) {
                    continue;
                }
                arquivos.add(new RequisicaoService.AnexoData(
                        fu.fileName(), fu.contentType(), Files.readAllBytes(fu.uploadedFile()),
                        CategoriaAnexo.NOTA_FISCAL));
            }
        }
        return service.finalizar(id, gasto, observacao, arquivos);
    }

    @POST
    @Path("/{id}/cancelar")
    @RolesAllowed({"LIDER", "ADMIN"})
    public RequisicaoResponse cancelar(@PathParam("id") Long id) {
        return service.cancelar(id);
    }

    /** Disparo manual do lembrete diário (teste/operacional). */
    @POST
    @Path("/cobrancas/executar")
    @RolesAllowed({"TESOUREIRO", "ADMIN"})
    public Map<String, Integer> executarCobrancas() {
        return Map.of("enviados", cobrancaService.enviarPendentes());
    }

    /** Binário de um anexo (nota fiscal) para download/preview. */
    @GET
    @Path("/anexos/{id}")
    @Produces(MediaType.WILDCARD)
    @RolesAllowed({"LIDER", "TESOUREIRO", "ADMIN"})
    public Response anexo(@PathParam("id") Long id) {
        RequisicaoAnexo a = service.obterAnexo(id);
        return Response.ok(a.getConteudo()).type(a.getTipo()).build();
    }
}
