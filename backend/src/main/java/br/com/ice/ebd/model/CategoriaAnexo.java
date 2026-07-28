package br.com.ice.ebd.model;

/** Categoria de um anexo da requisição. */
public enum CategoriaAnexo {
    /** Nota fiscal da prestação de contas (anexada pelo líder ao finalizar). */
    NOTA_FISCAL,
    /** Comprovante de transferência (anexado pelo tesoureiro ao aprovar). */
    COMPROVANTE,
    /** Comprovante da devolução do troco ao PIX da igreja (líder, ao finalizar quando gastou menos que o aprovado). */
    TROCO
}
