package br.com.ice.ebd.model;

/** Por que o aluno foi inativado. */
public enum MotivoInativacao {

    /** Automático: ultrapassou o limite de faltas seguidas sem justificativa (ChamadaService). */
    FALTAS_SEGUIDAS,

    /** Manual: alguém desmarcou "ativo" no cadastro do aluno. */
    MANUAL,

    /** Histórico anterior ao registro de inativações (migration V30) — sem data nem motivo. */
    NAO_REGISTRADO
}
