package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Classe;

public record ClasseResponse(
        Long id,
        String nome,
        String descricao,
        boolean ativo) {

    public static ClasseResponse de(Classe c) {
        return new ClasseResponse(c.getId(), c.getNome(), c.getDescricao(), c.isAtivo());
    }
}
