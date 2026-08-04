package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.AniversarianteResponse;
import br.com.ice.ebd.dto.BoletimResponse;
import br.com.ice.ebd.dto.MinhaFrequenciaResponse;
import br.com.ice.ebd.dto.QuizAlunoDto;
import br.com.ice.ebd.dto.TrocarSenhaRequest;
import br.com.ice.ebd.dto.UsoEventoRequest;
import br.com.ice.ebd.model.UsoEvento;
import br.com.ice.ebd.service.AcessoService;
import br.com.ice.ebd.service.AniversariantesService;
import br.com.ice.ebd.service.BoletimService;
import br.com.ice.ebd.service.MinhaFrequenciaService;
import br.com.ice.ebd.service.QuizAlunoService;
import br.com.ice.ebd.service.DesafiosService;
import br.com.ice.ebd.dto.MeuRankingResponse;
import br.com.ice.ebd.dto.RankingTurmasResponse;
import br.com.ice.ebd.service.UsuarioService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
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

    @Inject
    QuizAlunoService quizAlunoService;

    @Inject
    DesafiosService desafiosService;

    @Inject
    AniversariantesService aniversariantesService;

    @Inject
    AcessoService acessoService;

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

    /** Aniversariantes da escola (hoje + próximos 7 dias) para a tela inicial do aluno. */
    @GET
    @Path("/aniversariantes")
    @RolesAllowed("ALUNO")
    public List<AniversarianteResponse> aniversariantes() {
        return aniversariantesService.proximos();
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

    /** Ranking resumido da turma do próprio aluno (pódio + posição dele). */
    @GET
    @Path("/ranking")
    @RolesAllowed("ALUNO")
    public MeuRankingResponse ranking() {
        return desafiosService.resumoDoAluno();
    }

    /** Ranking de todas as turmas (desafio entre classes), destacando a turma do aluno. */
    @GET
    @Path("/ranking-turmas")
    @RolesAllowed("ALUNO")
    public RankingTurmasResponse rankingTurmas() {
        return desafiosService.resumoTurmasDoAluno();
    }

    /** Provas online da turma do aluno, com status (disponível/respondida/fechada/futura). */
    @GET
    @Path("/provas")
    @RolesAllowed("ALUNO")
    public List<QuizAlunoDto.ProvaResumo> minhasProvas() {
        return quizAlunoService.listarMinhas();
    }

    /** O quiz para responder (sem gabarito). Só dentro da janela e se ainda não respondida. */
    @GET
    @Path("/provas/{id}")
    @RolesAllowed("ALUNO")
    public QuizAlunoDto.ParaResponder responder(@PathParam("id") Long id) {
        return quizAlunoService.obterParaResponder(id);
    }

    /** Envia as respostas; corrige automaticamente e devolve a nota + o gabarito. */
    @POST
    @Path("/provas/{id}/submeter")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ALUNO")
    public QuizAlunoDto.Resultado submeter(@PathParam("id") Long id, QuizAlunoDto.SubmeterRequest req) {
        return quizAlunoService.submeter(id, req);
    }

    /** Resultado de uma prova já respondida (nota + gabarito, para rever). */
    @GET
    @Path("/provas/{id}/resultado")
    @RolesAllowed("ALUNO")
    public QuizAlunoDto.Resultado resultado(@PathParam("id") Long id) {
        return quizAlunoService.obterResultado(id);
    }

    /** Heartbeat leve: atualiza o last-seen do usuário (base do "online agora" no painel /uso). */
    @PUT
    @Path("/ping")
    @Authenticated
    public Response ping() {
        acessoService.ping(identity.getPrincipal().getName());
        return Response.noContent().build();
    }

    /** Instrumentação de uso (item D do painel /uso): registra a abertura de uma tela ou um clique notável. */
    @POST
    @Path("/evento")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    public Response evento(UsoEventoRequest req) {
        if (req != null) {
            acessoService.registrarEvento(identity.getPrincipal().getName(), req.recurso(), parseAcao(req.acao()));
        }
        return Response.noContent().build();
    }

    private static UsoEvento.Acao parseAcao(String acao) {
        if (acao != null && "CLICAR".equalsIgnoreCase(acao.trim())) {
            return UsoEvento.Acao.CLICAR;
        }
        return UsoEvento.Acao.ABRIR;
    }
}
