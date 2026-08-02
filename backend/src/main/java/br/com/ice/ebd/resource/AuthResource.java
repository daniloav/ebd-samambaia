package br.com.ice.ebd.resource;

import br.com.ice.ebd.dto.EsqueciSenhaRequest;
import br.com.ice.ebd.dto.LoginRequest;
import br.com.ice.ebd.dto.LoginResponse;
import br.com.ice.ebd.dto.RedefinirSenhaRequest;
import br.com.ice.ebd.service.AuthService;
import br.com.ice.ebd.service.RecuperacaoSenhaService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    RecuperacaoSenhaService recuperacao;

    @POST
    @Path("/login")
    @PermitAll
    public LoginResponse login(@Valid LoginRequest request,
                              @HeaderParam("User-Agent") String userAgent) {
        return authService.autenticar(request, userAgent);
    }

    /**
     * Passo 1: usuario informa o e-mail. Resposta SEMPRE generica (nao revela se o
     * e-mail existe). Se existir, enviamos o usuario + link de redefinicao por e-mail.
     */
    @POST
    @Path("/esqueci-senha")
    @PermitAll
    public Response esqueciSenha(EsqueciSenhaRequest req) {
        recuperacao.solicitar(req != null ? req.email() : null);
        return Response.ok(Map.of(
                "message", "Se o e-mail estiver cadastrado, enviamos as instrucoes para ele.")).build();
    }

    /** Passo 2: valida o token do link e devolve de qual usuario e (para a tela). */
    @GET
    @Path("/redefinir/{token}")
    @PermitAll
    public Map<String, String> validarToken(@PathParam("token") String token) {
        return Map.of("username", recuperacao.validar(token));
    }

    /** Passo 3: define a nova senha usando o token. */
    @POST
    @Path("/redefinir")
    @PermitAll
    public Response redefinir(RedefinirSenhaRequest req) {
        recuperacao.redefinir(req != null ? req.token() : null, req != null ? req.novaSenha() : null);
        return Response.noContent().build();
    }
}
