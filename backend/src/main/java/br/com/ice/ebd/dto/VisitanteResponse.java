package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Visitante;
import java.time.LocalDateTime;

public record VisitanteResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        Long trazidoPorId,
        String trazidoPorNome,
        LocalDateTime dataCadastro,
        /** Aviso gerado no cadastro (ex.: visitante promovido a aluno). Null nas listagens. */
        String alerta) {

    public static VisitanteResponse de(Visitante v) {
        return new VisitanteResponse(
                v.getId(), v.getNome(), v.getEmail(), v.getTelefone(),
                v.getTrazidoPor() != null ? v.getTrazidoPor().getId() : null,
                v.getTrazidoPor() != null ? v.getTrazidoPor().getNome() : null,
                v.getDataCadastro(), null);
    }

    /** Cópia com um aviso para exibir a quem cadastrou. */
    public VisitanteResponse comAlerta(String msg) {
        return new VisitanteResponse(id, nome, email, telefone, trazidoPorId, trazidoPorNome, dataCadastro, msg);
    }
}
