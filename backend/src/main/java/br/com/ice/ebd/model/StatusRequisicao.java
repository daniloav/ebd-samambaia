package br.com.ice.ebd.model;

/** Estados de uma requisição da tesouraria. */
public enum StatusRequisicao {
    ABERTA,       // aguardando avaliação do tesoureiro
    APROVADA,     // aprovada, aguardando prestação de contas (nota fiscal)
    NEGADA,       // rejeitada (terminal)
    FINALIZADA,   // nota fiscal anexada (terminal)
    CANCELADA     // cancelada pelo solicitante (terminal)
}
