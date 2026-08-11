package br.com.ice.ebd.model;

/**
 * De quem é a chave PIX informada na requisição.
 *
 * <p>{@link #PROPRIO} é o caso comum (o líder recebe e presta contas com nota fiscal) e a chave
 * é conferida contra o cadastro do solicitante. {@link #TERCEIRO} atende a <b>oferta de amor</b>:
 * o recurso vai direto para a conta da pessoa beneficiada, então a chave não é do solicitante —
 * nesses casos exige-se o <b>nome do beneficiário</b> e a prestação de contas é o
 * <b>comprovante da transferência</b>, não a nota fiscal.</p>
 */
public enum TitularChavePix {
    PROPRIO,
    TERCEIRO
}
