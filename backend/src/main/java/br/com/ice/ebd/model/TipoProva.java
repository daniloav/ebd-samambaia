package br.com.ice.ebd.model;

/**
 * OFFLINE = nota lançada pelo professor; ONLINE = quiz respondido pelo aluno (auto-corrigido);
 * RECUPERACAO = quiz de múltipla escolha vinculado a uma aula, com até 3 tentativas, que vale
 * pontos de presença (nota máxima = 1 presença) em vez de nota.
 */
public enum TipoProva {
    OFFLINE, ONLINE, RECUPERACAO;

    /** Tipos respondidos pelo aluno na tela (quiz auto-corrigido). */
    public boolean isQuiz() {
        return this != OFFLINE;
    }
}
