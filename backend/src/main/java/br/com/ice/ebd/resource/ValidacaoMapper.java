package br.com.ice.ebd.resource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converte erros de bean validation (@Valid) no mesmo JSON {"message","status"}
 * do {@link ErrorMapper}, para que o frontend exiba a razão real (ex.: "E-mail
 * inválido") em vez de um erro genérico.
 */
@Provider
public class ValidacaoMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException ex) {
        String mensagem = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining(" · "));
        if (mensagem.isBlank()) {
            mensagem = "Dados inválidos.";
        }
        return Response.status(400)
                .entity(Map.of("message", mensagem, "status", 400))
                .build();
    }
}
