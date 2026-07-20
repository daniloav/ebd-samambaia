-- ============================================================
-- V5: Campanhas (envio de e-mail em massa aos alunos com opt-in)
-- ============================================================
CREATE TABLE campanha (
    id             BIGSERIAL,
    titulo         VARCHAR(150) NOT NULL,
    mensagem       TEXT         NOT NULL,
    classe_id      BIGINT,               -- NULL = todas as turmas
    total_enviados INTEGER      NOT NULL DEFAULT 0,
    criado_por     VARCHAR(60),
    data_envio     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_campanha PRIMARY KEY (id),
    CONSTRAINT fk_campanha_classe FOREIGN KEY (classe_id) REFERENCES classe (id) ON DELETE SET NULL
);

CREATE INDEX idx_campanha_data ON campanha (data_envio);
