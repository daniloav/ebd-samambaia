package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.NotasProvaResponse;
import br.com.ice.ebd.dto.ProvaRequest;
import br.com.ice.ebd.dto.ProvaResponse;
import br.com.ice.ebd.dto.SalvarNotasRequest;
import br.com.ice.ebd.dto.QuizDto;
import br.com.ice.ebd.service.ProvaService;
import br.com.ice.ebd.service.QuizService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/api/provas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProvaResource {

    @Inject
    ProvaService service;

    @Inject
    QuizService quizService;

    @GET
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public List<ProvaResponse> listar(@jakarta.ws.rs.QueryParam("classeId") Long classeId) {
        return service.listar(classeId);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public ProvaResponse buscar(@PathParam("id") Long id) {
        return service.buscar(id);
    }

    @POST
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response criar(@Valid ProvaRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.criar(request)).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public ProvaResponse atualizar(@PathParam("id") Long id, @Valid ProvaRequest request) {
        return service.atualizar(id, request);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public Response deletar(@PathParam("id") Long id) {
        service.deletar(id);
        return Response.noContent().build();
    }

    // ----- Notas da prova -----

    @GET
    @Path("/{id}/notas")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public NotasProvaResponse obterNotas(@PathParam("id") Long id) {
        return service.obterNotas(id);
    }

    @PUT
    @Path("/{id}/notas")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public NotasProvaResponse salvarNotas(@PathParam("id") Long id, @Valid SalvarNotasRequest request) {
        return service.salvarNotas(id, request);
    }

    /** "Lançar e notificar": envia o desempenho por e-mail aos alunos com nota lançada. */
    @POST
    @Path("/{id}/notas/notificar")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Map<String, Integer> notificarNotas(@PathParam("id") Long id) {
        return Map.of("enviados", service.notificarNotas(id));
    }

    // ----- Questões da prova online (montador do professor) -----

    @GET
    @Path("/{id}/questoes")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public List<QuizDto.QuestaoEdit> questoes(@PathParam("id") Long id) {
        return quizService.obterQuestoes(id);
    }

    @PUT
    @Path("/{id}/questoes")
    @RolesAllowed({"ADMIN", "PROFESSOR"})
    public Response salvarQuestoes(@PathParam("id") Long id, QuizDto.Salvar request) {
        quizService.salvarQuestoes(id, request);
        return Response.noContent().build();
    }
}
