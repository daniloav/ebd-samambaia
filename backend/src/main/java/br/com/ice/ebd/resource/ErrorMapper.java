package br.com.ice.ebd.resource;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/** Converte WebApplicationException em JSON {"message": "..."} preservando o status. */
@Provider
public class ErrorMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException ex) {
        int status = ex.getResponse() != null ? ex.getResponse().getStatus() : 500;
        String mensagem = ex.getMessage() != null ? ex.getMessage() : "Erro inesperado";
        return Response.status(status)
                .entity(Map.of("message", mensagem, "status", status))
                .build();
    }
}
