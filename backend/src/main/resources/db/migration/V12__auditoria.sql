-- ============================================================
-- V12: auditoria de ações. Registra quem criou/alterou/excluiu
-- os cadastros de gestão (aluno, aula, prova, usuário) — quem, quando,
-- qual ação, qual entidade/registro. Tabela simples (sem diff de campos).
-- ============================================================
CREATE TABLE auditoria (
    id          BIGSERIAL PRIMARY KEY,
    data_hora   TIMESTAMP    NOT NULL DEFAULT NOW(),
    usuario     VARCHAR(60)  NOT NULL,                 -- login de quem fez a ação
    acao        VARCHAR(10)  NOT NULL,                 -- CRIAR | ATUALIZAR | EXCLUIR
    entidade    VARCHAR(20)  NOT NULL,                 -- ALUNO | AULA | PROVA | USUARIO
    entidade_id BIGINT,                                -- id do registro afetado
    descricao   VARCHAR(200)                           -- rótulo legível (nome/título/login)
);
CREATE INDEX idx_auditoria_data ON auditoria (data_hora DESC);
CREATE INDEX idx_auditoria_entidade ON auditoria (entidade);
